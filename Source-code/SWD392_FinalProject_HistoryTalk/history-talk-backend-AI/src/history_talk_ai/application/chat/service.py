import json
import uuid
import logging
import asyncio
import io
import wave
import re
import time
import unicodedata
from typing import List
import httpx
import numpy as np
from supabase import create_client, Client

from history_talk_ai.common.config.settings import settings
from history_talk_ai.dataaccess.java_backend.character_schema import CharacterData
from history_talk_ai.dataaccess.java_backend.historical_context_schema import HistoricalContextData
from history_talk_ai.presentation.chat.schemas import MessageHistoryItem, ProcessDocumentRequest
from history_talk_ai.application.prompting.prompt_builder import (
    build_chat_system_prompt,
    build_suggestion_prompt,
    character_pronouns,
    build_title_system_prompt,
)

logger = logging.getLogger(__name__)

# ── Supabase Client ───────────────────────────────────────────────────────────
supabase: Client = create_client(settings.SUPABASE_URL, settings.SUPABASE_KEY)

# ── Ollama HTTP Client ────────────────────────────────────────────────────────
# Compared without diacritics, so "tóm tắt" and "tom tat" both match
_BROAD_QUESTION_KEYWORDS = (
    "tom tat", "tong quan", "tong hop", "toan bo", "toan canh", "toan dien",
    "ke lai", "dien bien", "trinh bay", "phan tich", "so sanh", "ca tran", "ca cuoc",
)


class SpeechUnavailableError(Exception):
    """The TTS server could not produce audio."""


class RagUnavailableError(Exception):
    """Documents could not be searched, so an answer would not be grounded in them."""


_CJK_PATTERN = re.compile(r"[\u3400-\u9fff\uf900-\ufaff]")

# Use a global client to reuse connections (Connection Pooling) for faster requests
# A short connect timeout makes a stopped GPU server fail in seconds instead of minutes
_LLM_TIMEOUT = httpx.Timeout(120.0, connect=5.0)
ollama_client = httpx.AsyncClient(
    timeout=_LLM_TIMEOUT,
    follow_redirects=True,
    headers={"ngrok-skip-browser-warning": "true", "User-Agent": "HistoryTalk-AI/1.0"}
)


def _ollama_url(path: str) -> str:
    return f"{settings.OLLAMA_BASE_URL.rstrip('/')}{path}"


def _vision_ollama_url(path: str) -> str:
    base_url = settings.VISION_OLLAMA_BASE_URL.strip() if settings.VISION_OLLAMA_BASE_URL else settings.OLLAMA_BASE_URL.strip()
    return f"{base_url.rstrip('/')}{path}"

async def _call_ollama(messages: list[dict], expect_json: bool = True, temperature: float | None = None) -> tuple[str, int, int]:
    """Make an async call to the Ollama endpoint. Returns (content, prompt_tokens, completion_tokens)"""
    payload = {
        "model": settings.LLM_MODEL,
        "messages": messages,
        "stream": False,
        "think": False,
        "options": {
            "temperature": settings.LLM_TEMPERATURE if temperature is None else temperature,
            "num_predict": settings.LLM_MAX_TOKENS,
            "num_ctx": 8192,
            "repeat_penalty": 1.15,
        }
    }
    
    if expect_json:
        payload["format"] = "json"

    auth = (settings.OLLAMA_USERNAME, settings.OLLAMA_PASSWORD) if settings.OLLAMA_USERNAME else None

    try:
        response = await ollama_client.post(
            _ollama_url("/api/chat"),
            json=payload,
            auth=auth
        )
        response.raise_for_status()
        data = response.json()
        content = data.get("message", {}).get("content", "")
        prompt_tokens = data.get("prompt_eval_count", 0)
        prompt_tokens = max(1, prompt_tokens // 10) if prompt_tokens > 0 else 0
        completion_tokens = data.get("eval_count", 0)
        return content, prompt_tokens, completion_tokens
    except Exception as e:
        logger.error(f"Failed to call Ollama: {e}")
        raise

async def _call_ollama_stream(messages: list[dict]) -> tuple:
    """Make an async call to the Ollama endpoint with streaming enabled. Yields chunks and finally returns tokens."""
    payload = {
        "model": settings.LLM_MODEL,
        "messages": messages,
        "stream": True,
        "think": False,
        "options": {
            "temperature": settings.LLM_TEMPERATURE,
            "num_predict": settings.LLM_MAX_TOKENS,
            "num_ctx": 8192,
            "repeat_penalty": 1.15,
        }
    }
    
    auth = (settings.OLLAMA_USERNAME, settings.OLLAMA_PASSWORD) if settings.OLLAMA_USERNAME else None

    prompt_tokens = 0
    completion_tokens = 0

    try:
        async with ollama_client.stream(
            "POST",
            _ollama_url("/api/chat"),
            json=payload,
            auth=auth,
            timeout=_LLM_TIMEOUT
        ) as response:
            response.raise_for_status()
            async for line in response.aiter_lines():
                if not line:
                    continue
                try:
                    data = json.loads(line)
                    chunk = data.get("message", {}).get("content", "")
                    if chunk:
                        yield chunk
                    if data.get("done"):
                        prompt_tokens = data.get("prompt_eval_count", 0)
                        prompt_tokens = max(1, prompt_tokens // 10) if prompt_tokens > 0 else 0
                        completion_tokens = data.get("eval_count", 0)
                except json.JSONDecodeError:
                    continue
    except Exception as e:
        logger.error(f"Failed to stream from Ollama: {e}")
        raise
        
    yield {"prompt_tokens": prompt_tokens, "completion_tokens": completion_tokens}

async def _call_external(messages: list[dict], expect_json: bool = True, temperature: float | None = None) -> tuple[str, int, int]:
    """Make an async call to an external OpenAI-compatible API (e.g. OpenAI, Gemini, Groq)."""
    api_key = settings.EXTERNAL_API_KEY or settings.GOOGLE_API_KEY
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }
    
    payload = {
        "model": settings.LLM_MODEL,
        "messages": messages,
        "temperature": settings.LLM_TEMPERATURE if temperature is None else temperature,
        "max_tokens": settings.LLM_MAX_TOKENS,
        "stream": False,
    }
    
    if expect_json:
        payload["response_format"] = {"type": "json_object"}

    try:
        response = await ollama_client.post(
            f"{settings.EXTERNAL_API_BASE_URL.rstrip('/')}/chat/completions",
            json=payload,
            headers=headers
        )
        response.raise_for_status()
        data = response.json()
        content = data["choices"][0]["message"].get("content", "")
        usage = data.get("usage", {})
        return content, usage.get("prompt_tokens", 0), usage.get("completion_tokens", 0)
    except Exception as e:
        logger.error(f"Failed to call external API: {e}")
        raise

async def _call_external_stream(messages: list[dict]) -> tuple:
    """Make an async call to an external API with streaming enabled."""
    api_key = settings.EXTERNAL_API_KEY or settings.GOOGLE_API_KEY
    headers = {
        "Authorization": f"Bearer {api_key}",
        "Content-Type": "application/json"
    }
    
    payload = {
        "model": settings.LLM_MODEL,
        "messages": messages,
        "temperature": settings.LLM_TEMPERATURE,
        "max_tokens": settings.LLM_MAX_TOKENS,
        "stream": True,
    }
    
    prompt_tokens = 0
    completion_tokens = 0

    try:
        async with ollama_client.stream(
            "POST",
            f"{settings.EXTERNAL_API_BASE_URL.rstrip('/')}/chat/completions",
            json=payload,
            headers=headers,
            timeout=_LLM_TIMEOUT
        ) as response:
            response.raise_for_status()
            async for line in response.aiter_lines():
                if not line or not line.startswith("data: "):
                    continue
                data_str = line[6:]
                if data_str.strip() == "[DONE]":
                    break
                try:
                    data = json.loads(data_str)
                    choices = data.get("choices", [])
                    if choices:
                        delta = choices[0].get("delta", {})
                        chunk = delta.get("content", "")
                        if chunk:
                            yield chunk
                    # Some APIs send usage in the last chunk
                    usage = data.get("usage")
                    if usage:
                        prompt_tokens = usage.get("prompt_tokens", 0)
                        completion_tokens = usage.get("completion_tokens", 0)
                except json.JSONDecodeError:
                    continue
    except Exception as e:
        logger.error(f"Failed to stream from external API: {e}")
        raise
        
    yield {"prompt_tokens": prompt_tokens, "completion_tokens": completion_tokens}

def _is_broad_question(question: str) -> bool:
    """A question about a whole event needs more chunks than a question about one fact."""
    decomposed = unicodedata.normalize("NFD", question.lower().replace("đ", "d"))
    plain = "".join(ch for ch in decomposed if unicodedata.category(ch) != "Mn")
    return any(keyword in plain for keyword in _BROAD_QUESTION_KEYWORDS)


def _contains_cjk(text: str) -> bool:
    return _CJK_PATTERN.search(text) is not None


async def _call_provider(messages: list[dict], expect_json: bool, temperature: float | None) -> tuple[str, int, int]:
    if settings.LLM_PROVIDER.lower() in ("external", "google"):
        return await _call_external(messages, expect_json, temperature)
    return await _call_ollama(messages, expect_json, temperature)


async def _call_llm(messages: list[dict], expect_json: bool = True, temperature: float | None = None) -> tuple[str, int, int]:
    """Call the configured provider, retrying once if the reply leaks Chinese into a Vietnamese conversation."""
    content, prompt_tokens, completion_tokens = await _call_provider(messages, expect_json, temperature)

    # Sources may legitimately quote Hán characters, in which case the reply may quote them too.
    if not _contains_cjk(content) or any(_contains_cjk(m.get("content", "")) for m in messages):
        return content, prompt_tokens, completion_tokens

    logger.warning("LLM reply contains Chinese characters, retrying once.")
    content, retry_prompt_tokens, retry_completion_tokens = await _call_provider(messages, expect_json, temperature)
    if _contains_cjk(content):
        raise ValueError("LLM reply still contains Chinese characters after retrying.")
    return content, prompt_tokens + retry_prompt_tokens, completion_tokens + retry_completion_tokens

async def _call_llm_stream(messages: list[dict]) -> tuple:
    """Router to stream from either Ollama or External API based on settings."""
    if settings.LLM_PROVIDER.lower() in ("external", "google"):
        async for chunk in _call_external_stream(messages):
            yield chunk
    else:
        async for chunk in _call_ollama_stream(messages):
            yield chunk

class _PcmDownsampler:
    """Averages every `factor` samples of 16-bit mono PCM, keeping partial samples between chunks."""

    def __init__(self, factor: int):
        self._frame = factor * 2
        self._pending = b""

    def feed(self, chunk: bytes) -> bytes:
        data = self._pending + chunk
        usable = len(data) - len(data) % self._frame
        self._pending = data[usable:]
        if not usable:
            return b""
        samples = np.frombuffer(data[:usable], dtype="<i2").reshape(-1, self._frame // 2)
        return samples.mean(axis=1).round().astype("<i2").tobytes()


def _downsample_factor() -> int:
    factor = settings.TTS_SOURCE_SAMPLE_RATE // settings.TTS_OUTPUT_SAMPLE_RATE
    return max(1, factor)


async def open_speech_stream(text: str, voice: str) -> httpx.Response:
    """Start streaming raw PCM from the TTS server. Fails before any audio is sent to the client."""
    auth = (settings.OLLAMA_USERNAME, settings.OLLAMA_PASSWORD) if settings.OLLAMA_USERNAME else None
    request = ollama_client.build_request(
        "POST",
        _ollama_url("/v1/audio/speech"),
        json={"model": settings.TTS_MODEL, "input": text, "voice": voice, "response_format": "pcm"},
    )
    try:
        response = await ollama_client.send(request, auth=auth, stream=True)
    except Exception as e:
        logger.error(f"TTS request failed: {e}")
        raise SpeechUnavailableError("Dịch vụ giọng đọc đang không khả dụng. Vui lòng thử lại sau.") from e

    if response.status_code >= 400:
        await response.aread()
        await response.aclose()
        logger.error(f"TTS server returned {response.status_code}: {response.text[:200]}")
        raise SpeechUnavailableError("Dịch vụ giọng đọc đang không khả dụng. Vui lòng thử lại sau.")
    return response


async def iter_speech_pcm(response: httpx.Response):
    """Yield 16-bit mono PCM at TTS_OUTPUT_SAMPLE_RATE as soon as the TTS server produces it."""
    downsampler = _PcmDownsampler(_downsample_factor())
    try:
        async for chunk in response.aiter_bytes():
            pcm = downsampler.feed(chunk)
            if pcm:
                yield pcm
    finally:
        await response.aclose()


async def synthesize_speech(text: str, voice: str) -> bytes:
    """Return the whole sentence as a WAV file at TTS_OUTPUT_SAMPLE_RATE."""
    response = await open_speech_stream(text, voice)
    try:
        pcm = b"".join([chunk async for chunk in iter_speech_pcm(response)])
    except Exception as e:
        logger.error(f"TTS stream failed: {e}")
        raise SpeechUnavailableError("Dịch vụ giọng đọc đang không khả dụng. Vui lòng thử lại sau.") from e

    if not pcm:
        raise SpeechUnavailableError("Dịch vụ giọng đọc không trả về âm thanh.")

    buffer = io.BytesIO()
    with wave.open(buffer, "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(settings.TTS_OUTPUT_SAMPLE_RATE)
        wav.writeframes(pcm)
    return buffer.getvalue()


async def get_embedding_from_ollama(text: str, max_retries: int = 3) -> List[float]:
    """Get embedding vector for a given text from Ollama with retries."""
    if not text or not text.strip():
        return []
        
    payload = {
        "model": "bge-m3",
        "prompt": text
    }
    auth = (settings.OLLAMA_USERNAME, settings.OLLAMA_PASSWORD) if settings.OLLAMA_USERNAME else None

    for attempt in range(max_retries):
        try:
            response = await ollama_client.post(_ollama_url("/api/embeddings"), json=payload, auth=auth)
            if response.status_code == 404:
                # Fallback to Ollama /api/embed endpoint
                embed_payload = {"model": "bge-m3", "input": text}
                response = await ollama_client.post(_ollama_url("/api/embed"), json=embed_payload, auth=auth)
                response.raise_for_status()
                data = response.json()
                embeddings = data.get("embeddings", [])
                if embeddings and isinstance(embeddings, list) and len(embeddings) > 0:
                    return embeddings[0] if isinstance(embeddings[0], list) else embeddings
            else:
                response.raise_for_status()
                data = response.json()
                embedding = data.get("embedding", [])
                if embedding:
                    return embedding
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            logger.error(f"Cannot reach the LLM server for embedding: {e}")
            return []
        except Exception as e:
            logger.warning(f"Ollama embedding attempt {attempt + 1}/{max_retries} failed: {e}")
            if attempt < max_retries - 1:
                await asyncio.sleep(1.0 * (attempt + 1))  # Exponential backoff
                
    logger.error("All attempts to get embedding from Ollama failed.")
    return []

async def retrieve_history_context(user_question: str, entity_ids: List[str]) -> tuple[str, List[str]]:
    """Retrieve history context from Supabase VectorChunk and rerank via the LLM server."""
    query_vector = await get_embedding_from_ollama(user_question)
    if not query_vector:
        raise RagUnavailableError("Không kết nối được máy chủ AI để tra cứu tư liệu. Vui lòng thử lại sau.")

    try:
        response = supabase.schema(settings.SUPABASE_SCHEMA).rpc(
            "match_history_chunks",
            {
                "query_embedding": query_vector,
                "match_limit": 10, # Lấy nhiều hơn để rerank
                "filter_entity_ids": entity_ids
            }
        ).execute()
        
        chunks = response.data
        if not chunks:
            return "", []
        texts = [chunk.get("content", "") for chunk in chunks if chunk.get("content")]
        
        # Gọi sang LLM server để Rerank
        try:
            auth = (settings.OLLAMA_USERNAME, settings.OLLAMA_PASSWORD) if settings.OLLAMA_USERNAME else None
            rerank_res = await ollama_client.post(
                _ollama_url("/api/rerank"),
                json={"query": user_question, "documents": texts},
                auth=auth,
            )
            rerank_res.raise_for_status()
            is_broad = _is_broad_question(user_question)
            top_k = settings.RAG_TOP_K_BROAD if is_broad else settings.RAG_TOP_K
            top_docs = rerank_res.json().get("top_docs", [])[:top_k]
            # A general question scores low against every specific chunk, which does not make them irrelevant.
            if is_broad and not top_docs:
                top_docs = texts[:top_k]
        except Exception as e:
            logger.error(f"Rerank API failed: {e}. Fallback to top 2.")
            top_docs = texts[:2]
            
        context_str = "\n\n".join(top_docs)
        return context_str, top_docs
    except Exception as e:
        logger.error(f"Supabase RPC error: {e}")
        raise RagUnavailableError("Không tra cứu được tư liệu lịch sử. Vui lòng thử lại sau.") from e


def _build_final_user_content(user_message: str, rag_context: str, self_pronoun: str = "ta") -> str:
    """Put the grounding rules next to the question, where the model follows them most closely."""
    if not rag_context:
        return (
            "[KHÔNG TÌM THẤY TƯ LIỆU CHO CÂU NÀY]\n"
            "LỆNH: Chỉ dùng THÔNG TIN LỊCH SỬ CỦA BẠN. Chào hỏi hoặc chuyện thường ngày thì trả lời tự nhiên. "
            "Hỏi về sự kiện, con số, tên người hay địa danh không có ở đó thì BẮT BUỘC đáp 'Sử sách không ghi chép rõ', không suy diễn.\n\n"
            f"CÂU HỎI CỦA NGƯỜI DÙNG: {user_message}"
        )
    broad_rule = (
        "4. Câu này cần tổng hợp nhiều sự việc: được trả lời tối đa 200 từ, kể theo trình tự thời gian.\n"
        if _is_broad_question(user_message)
        else ""
    )
    return (
        f"[DỮ LIỆU THAM KHẢO CHO CÂU HỎI NÀY]:\n{rag_context}\n\n"
        "LỆNH:\n"
        f"1. Chỉ dùng sự kiện có trong DỮ LIỆU THAM KHẢO, kể lại bằng lời của chính mình ở ngôi thứ nhất (\"{self_pronoun}\"), "
        "không gọi tên mình ở ngôi thứ ba, bỏ các số chú thích như (1), [1].\n"
        "2. KHÔNG bịa đặt hay sáng tác thêm. Dữ liệu không nhắc tới thì nói 'Sử sách không ghi chép rõ'.\n"
        "3. Không lặp lại y hệt câu trả lời trước đó.\n"
        "4. Nếu người dùng hỏi xem ảnh/bản đồ hoặc dữ liệu tham khảo có chứa đường dẫn ảnh tư liệu (URL), HÃY chèn ảnh bằng cú pháp Markdown: ![Tên ảnh](URL).\n"
        f"{broad_rule}\n"
        f"CÂU HỎI CỦA NGƯỜI DÙNG: {user_message}"
    )


_SUGGESTION_CANDIDATES = 5
_MAX_SUGGESTIONS = 3


async def _has_supporting_document(question: str, entity_ids: List[str]) -> bool | None:
    """True when a document chunk passes the reranker for this question, None when it could not be checked."""
    query_vector = await get_embedding_from_ollama(question)
    if not query_vector:
        return None
    try:
        response = await asyncio.to_thread(
            lambda: supabase.schema(settings.SUPABASE_SCHEMA).rpc(
                "match_history_chunks",
                {"query_embedding": query_vector, "match_limit": 5, "filter_entity_ids": entity_ids},
            ).execute()
        )
        texts = [chunk.get("content", "") for chunk in response.data or [] if chunk.get("content")]
        if not texts:
            return False

        auth = (settings.OLLAMA_USERNAME, settings.OLLAMA_PASSWORD) if settings.OLLAMA_USERNAME else None
        rerank_res = await ollama_client.post(
            _ollama_url("/api/rerank"),
            json={"query": question, "documents": texts},
            auth=auth,
        )
        rerank_res.raise_for_status()
        return bool(rerank_res.json().get("top_docs"))
    except Exception as e:
        logger.warning(f"Could not check a suggested question against the documents: {e}")
        return None


async def _generate_suggested_questions(
    character: CharacterData, rag_context: str, reply: str, entity_ids: List[str]
) -> tuple[List[str], int, int]:
    """
    Suggest questions the character can actually answer: the prompt asks for questions answered by the
    documents, then each candidate is checked against the documents and dropped when none supports it.
    Suggestions are optional, so failing to generate them must never fail the reply itself.
    """
    prompt = build_suggestion_prompt(character, rag_context, reply, _SUGGESTION_CANDIDATES)
    try:
        sq_text, prompt_tokens, completion_tokens = await _call_llm(
            [{"role": "user", "content": prompt}], expect_json=False
        )
    except Exception as e:
        logger.warning(f"Failed to generate suggested questions: {e}")
        return [], 0, 0

    candidates = []
    for line in sq_text.strip().split("\n"):
        clean_line = re.sub(r"^[\-\*\•\—\–\+]\s*|^\d+[\.\)]\s*", "", line.strip()).strip()
        clean_line = clean_line.strip('"').strip("'")
        if len(clean_line) > 5 and clean_line not in candidates:
            candidates.append(clean_line)
    candidates = candidates[:_SUGGESTION_CANDIDATES]

    # A candidate that could not be checked is kept: the documents were reachable for the reply itself.
    supported = await asyncio.gather(*[_has_supporting_document(q, entity_ids) for q in candidates])
    questions = [q for q, ok in zip(candidates, supported) if ok is not False]
    return questions[:_MAX_SUGGESTIONS], prompt_tokens, completion_tokens


# ── Public service functions ──────────────────────────────────────────────────

async def generate_reply(
    character: CharacterData,
    context: HistoricalContextData | None,
    user_message: str,
    message_history: List[MessageHistoryItem],
    skip_suggestions: bool = False,
) -> tuple[str, List[str], int, int, List[str]]:
    """
    Invoke the LLM in character-roleplay mode.

    Returns:
        (assistant_message, suggested_questions, prompt_tokens, completion_tokens, quotes_used)
    """
    # ── RAG Integration ──
    # Query both context documents and character documents
    entity_ids = [character.characterId]
    if context:
        entity_ids.append(context.contextId)
        
    if character.contexts:
        for ctx in character.contexts:
            if ctx.contextId not in entity_ids:
                entity_ids.append(ctx.contextId)
                
    if character.events:
        for evt in character.events:
            if evt.id and evt.id not in entity_ids:
                entity_ids.append(evt.id)
                
    rag_context = ""
    quotes_used = []
    rag_context, quotes_used = await retrieve_history_context(user_message, entity_ids)
    
    system_prompt = build_chat_system_prompt(character, context)
    
    messages = [{"role": "system", "content": system_prompt}]
    
    # Inject conversation history
    for item in message_history:
        messages.append({"role": item.role, "content": item.content})
        
    # Inject RAG context vào câu hỏi cuối cùng để AI tập trung nhất
    final_user_content = _build_final_user_content(user_message, rag_context, character_pronouns(character)[0])

    messages.append({"role": "user", "content": final_user_content})

    response_text, prompt_tokens, completion_tokens = await _call_llm(messages, expect_json=False)
    
    suggested_questions = []
    sq_pt = 0
    sq_ct = 0
    
    if not skip_suggestions:
        suggested_questions, sq_pt, sq_ct = await _generate_suggested_questions(character, rag_context, response_text, entity_ids)

    return response_text.strip(), suggested_questions, prompt_tokens + sq_pt, completion_tokens + sq_ct, quotes_used

async def generate_reply_stream(
    character: CharacterData,
    context: HistoricalContextData | None,
    user_message: str,
    message_history: List[MessageHistoryItem],
    skip_suggestions: bool = False,
):
    """
    Invoke the LLM in character-roleplay mode using streaming.
    Yields text chunks. The final yield is a dict containing metadata.
    """
    entity_ids = [character.characterId]
    if context:
        entity_ids.append(context.contextId)
        
    if character.contexts:
        for ctx in character.contexts:
            if ctx.contextId not in entity_ids:
                entity_ids.append(ctx.contextId)
                
    if character.events:
        for evt in character.events:
            if evt.id and evt.id not in entity_ids:
                entity_ids.append(evt.id)
                
    rag_context = ""
    quotes_used = []
    started_at = time.perf_counter()
    rag_context, quotes_used = await retrieve_history_context(user_message, entity_ids)
    rag_seconds = time.perf_counter() - started_at
    
    system_prompt = build_chat_system_prompt(character, context)
    
    messages = [{"role": "system", "content": system_prompt}]
    for item in message_history:
        messages.append({"role": item.role, "content": item.content})
        
    # Inject RAG context vào câu hỏi cuối cùng để AI tập trung nhất
    final_user_content = _build_final_user_content(user_message, rag_context, character_pronouns(character)[0])

    messages.append({"role": "user", "content": final_user_content})

    full_message = ""
    prompt_tokens = 0
    completion_tokens = 0

    # Stream the message
    stream_gen = _call_llm_stream(messages)
    async for chunk in stream_gen:
        if isinstance(chunk, dict):
            prompt_tokens = chunk.get("prompt_tokens", 0)
            completion_tokens = chunk.get("completion_tokens", 0)
        else:
            if not full_message:
                logger.info(f"Chat stream timing: rag={rag_seconds:.2f}s first_token={time.perf_counter() - started_at:.2f}s")
            full_message += chunk
            yield chunk

    if _contains_cjk(full_message) and not any(_contains_cjk(m["content"]) for m in messages):
        logger.warning("Streamed LLM reply contains Chinese characters.")

    suggested_questions = []
    sq_pt = 0
    sq_ct = 0

    if not skip_suggestions:
        suggested_questions, sq_pt, sq_ct = await _generate_suggested_questions(character, rag_context, full_message, entity_ids)

    yield {
        "suggestedQuestions": suggested_questions,
        "promptTokens": prompt_tokens + sq_pt,
        "completionTokens": completion_tokens + sq_ct,
        "quotes_used": quotes_used
    }

async def generate_session_title(
    character: CharacterData,
    first_user_message: str,
    first_assistant_message: str,
) -> tuple[str, int, int]:
    """Generate a short session title from the first exchange. Returns (title, prompt_tokens, completion_tokens)"""
    system_prompt = build_title_system_prompt(character)
    
    json_instruction = "\n\nCHỈ TRẢ VỀ 1 CÂU TIÊU ĐỀ NGẮN GỌN DƯỚI 8 TỪ. KHÔNG GIẢI THÍCH."
    system_prompt += json_instruction
    
    conversation_snippet = (
        f"Người dùng: {first_user_message}\n"
        f"{character.name}: {first_assistant_message}"
    )
    
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": conversation_snippet}
    ]

    response_text, prompt_tokens, completion_tokens = await _call_llm(messages, expect_json=False)
    
    title = response_text.strip().strip('"').strip("'")
    if title:
        return title, prompt_tokens, completion_tokens
    return "Cuộc trò chuyện mới", prompt_tokens, completion_tokens

async def process_document(request: ProcessDocumentRequest):
    """Process a document by chunking, embedding, and storing in Supabase."""
    # Delete old chunks for this doc_id to handle upserts properly
    try:
        supabase.schema(settings.SUPABASE_SCHEMA).table("vector_chunk").delete().eq("doc_id", request.doc_id).execute()
    except Exception as e:
        logger.error(f"Failed to delete old chunks for doc {request.doc_id}: {e}")

def _cosine_distance(vec1: List[float], vec2: List[float]) -> float:
    """Calculate cosine distance between two vectors: 1.0 - cosine_similarity."""
    if not vec1 or not vec2 or len(vec1) != len(vec2):
        return 1.0
    dot_product = sum(a * b for a, b in zip(vec1, vec2))
    norm_a = (sum(a * a for a in vec1)) ** 0.5
    norm_b = (sum(b * b for b in vec2)) ** 0.5
    if norm_a == 0 or norm_b == 0:
        return 1.0
    similarity = dot_product / (norm_a * norm_b)
    return 1.0 - max(-1.0, min(1.0, similarity))

async def _semantic_chunking(content: str, max_chunk_chars: int = 1200) -> List[str]:
    """
    Perform dynamic semantic boundary detection using sentence embeddings (BGE-M3).
    Breakpoints are calculated dynamically when the cosine distance between consecutive 
    sentence embeddings exceeds dynamic percentile variance thresholds.
    (Reference: Vietnamese History RAG Paper - Section 3.2).
    """
    if not content or not content.strip():
        return []

    import re
    raw_sentences = re.split(r'(?<=[.!?\n])\s+', content)
    sentences = [s.strip() for s in raw_sentences if s and s.strip()]

    if not sentences:
        return [content]

    if len(sentences) <= 2 or len(content) <= 500:
        return [content]

    sem = asyncio.Semaphore(10)
    async def get_emb(s):
        async with sem:
            return await get_embedding_from_ollama(s)

    embeddings = await asyncio.gather(*[get_emb(s) for s in sentences])

    valid_sentences = []
    valid_embeddings = []
    for s, emb in zip(sentences, embeddings):
        if emb:
            valid_sentences.append(s)
            valid_embeddings.append(emb)

    if len(valid_sentences) <= 2:
        return [content]

    distances = []
    for i in range(len(valid_embeddings) - 1):
        dist = _cosine_distance(valid_embeddings[i], valid_embeddings[i + 1])
        distances.append(dist)

    if distances:
        sorted_dists = sorted(distances)
        pct_idx = int(len(sorted_dists) * 0.80)
        threshold = max(0.35, sorted_dists[min(pct_idx, len(sorted_dists) - 1)])
    else:
        threshold = 0.35

    chunks = []
    current_chunk_sentences = [valid_sentences[0]]
    current_len = len(valid_sentences[0])

    for i in range(len(distances)):
        dist = distances[i]
        next_sentence = valid_sentences[i + 1]

        if dist >= threshold or (current_len + len(next_sentence) > max_chunk_chars):
            chunks.append(" ".join(current_chunk_sentences))
            current_chunk_sentences = [next_sentence]
            current_len = len(next_sentence)
        else:
            current_chunk_sentences.append(next_sentence)
            current_len += len(next_sentence) + 1

    if current_chunk_sentences:
        chunks.append(" ".join(current_chunk_sentences))

    return chunks

async def process_document(request: ProcessDocumentRequest):
    """Process a document by semantic chunking, embedding, and storing in Supabase."""
    # Delete old chunks for this doc_id to handle upserts properly
    try:
        supabase.schema(settings.SUPABASE_SCHEMA).table("vector_chunk").delete().eq("doc_id", request.doc_id).execute()
    except Exception as e:
        logger.error(f"Failed to delete old chunks for doc {request.doc_id}: {e}")

    content = request.content
    chunks = await _semantic_chunking(content)
    
    # Generate embeddings concurrently for speed, but limit concurrency to avoid overwhelming Ollama
    sem = asyncio.Semaphore(15) # Process maximum 15 chunks at a time

    async def get_embedding_with_context(idx, chunk_text):
        async with sem:
            emb = await get_embedding_from_ollama(chunk_text)
            return idx, chunk_text, emb

    tasks = [get_embedding_with_context(idx, text) for idx, text in enumerate(chunks)]
    results = await asyncio.gather(*tasks)

    records = []
    for idx, chunk_text, embedding in results:
        if not embedding:
            logger.warning(f"Failed to generate embedding for chunk {idx} of document {request.doc_id}")
            continue
            
        records.append({
            "chunk_id": str(uuid.uuid4()),
            "doc_id": request.doc_id,
            "entity_id": request.entity_id,
            "content": chunk_text,
            "embedding": embedding,
            "sequence_number": idx + 1
        })
        
    if records:
        # Bulk insert in batches of 100 to avoid request size limits
        batch_size = 100
        for i in range(0, len(records), batch_size):
            batch = records[i:i + batch_size]
            try:
                supabase.schema(settings.SUPABASE_SCHEMA).table("vector_chunk").insert(batch).execute()
                logger.info(f"Inserted batch {i//batch_size + 1} ({len(batch)} chunks) for doc {request.doc_id}")
            except Exception as e:
                logger.error(f"Failed to insert batch {i//batch_size + 1} into Supabase: {e}")
                raise

async def delete_document(doc_id: str):
    """Delete all chunks for a document from Supabase."""
    try:
        supabase.schema(settings.SUPABASE_SCHEMA).table("vector_chunk").delete().eq("doc_id", doc_id).execute()
        logger.info(f"Successfully deleted chunks for doc {doc_id}")
    except Exception as e:
        logger.error(f"Failed to delete chunks for doc {doc_id}: {e}")
        raise


async def describe_historical_image(image_base64: str, caption: str = None) -> str:
    """
    Describe a historical image.
    """
    caption_info = caption if caption else "Không có chú thích"

    # Kiểm tra tính hợp lệ của chuỗi base64 (tránh gửi chuỗi rỗng gây 400 Bad Request cho Ollama)
    if not image_base64 or not isinstance(image_base64, str):
        fallback_name = caption or 'Ảnh tư liệu'
        return f"[TƯ LIỆU HÌNH ẢNH: {fallback_name}]\n- Chú giải tư liệu: {caption_info}\n[HẾT TƯ LIỆU HÌNH ẢNH]"

    # Clean base64 header if present (e.g., data:image/png;base64,...)
    mime_type = "image/jpeg"
    raw_b64 = image_base64.strip()
    if "," in raw_b64 and "base64" in raw_b64:
        header, raw_b64 = raw_b64.split(",", 1)
        if "image/png" in header:
            mime_type = "image/png"
        elif "image/webp" in header:
            mime_type = "image/webp"

    raw_b64 = "".join(raw_b64.split())
    if len(raw_b64) < 100:
        fallback_name = caption or 'Ảnh tư liệu'
        return f"[TƯ LIỆU HÌNH ẢNH: {fallback_name}]\n- Chú giải tư liệu: {caption_info}\n[HẾT TƯ LIỆU HÌNH ẢNH]"
    prompt_text = (
        "Bạn là chuyên gia thẩm định tư liệu lịch sử Việt Nam phục vụ giáo dục học tập. Nguyên tắc tối cao: TRUNG THỰC VỚI NGUYÊN BẢN & DỄ HIỂU CHO HỌC TẬP.\n"
        f"- Chú thích gốc trong tài liệu: {caption_info}\n\n"
        "Nhiệm vụ: Phân tích khách quan những gì TRỰC TIẾP NHÌN THẤY trên hình ảnh/lược đồ này theo cấu trúc Markdown sau:\n"
        "[TƯ LIỆU HÌNH ẢNH]\n"
        "- Phân loại: (Lược đồ tác chiến / Bản đồ chiến dịch / Ảnh tư liệu hiện trường / Chân dung nhân vật / Hiện vật di tích)\n"
        "- Nội dung chi tiết trên ảnh: (Ghi nhận rõ ràng các địa danh, cứ điểm, sông núi, hướng tiến công/phòng ngự, mốc thời gian, tên nhân vật. Nếu trên bản đồ có chữ tiếng Pháp/ngoại ngữ thì mở ngoặc dịch nghĩa tiếng Việt ngay bên cạnh; bỏ qua các thông số trắc địa in ấn ngoài lề không phục vụ bài học)\n"
        "- Chú giải khách quan: (Tóm tắt ngắn gọn 1-2 câu về sự kiện lịch sử được phản ánh trực tiếp trong hình)\n"
        "- Ghi chú thẩm định: (Nếu chi tiết nào mờ hoặc không có trong hình, ghi rõ: 'Chi tiết trên ảnh không thể hiện')\n"
        "[HẾT TƯ LIỆU HÌNH ẢNH]"
    )

    # 1. Thử gọi Google Gemini nếu cấu hình provider là google và có api_key
    if settings.VISION_PROVIDER.lower() == "google" and settings.GOOGLE_API_KEY:
        api_key = settings.GOOGLE_API_KEY
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{settings.VISION_MODEL}:generateContent?key={api_key}"
        payload = {
            "contents": [
                {
                    "parts": [
                        {"text": prompt_text},
                        {
                            "inline_data": {
                                "mime_type": mime_type,
                                "data": raw_b64.strip()
                            }
                        }
                    ]
                }
            ],
            "generationConfig": {
                "temperature": 0.2,
                "maxOutputTokens": 1024
            }
        }
        try:
            res = await ollama_client.post(url, json=payload, timeout=45.0)
            res.raise_for_status()
            data = res.json()
            candidates = data.get("candidates", [])
            if candidates:
                parts = candidates[0].get("content", {}).get("parts", [])
                if parts:
                    return parts[0].get("text", "").strip()
            fallback_name = caption or 'Ảnh tư liệu'
            return f"[TƯ LIỆU HÌNH ẢNH: {fallback_name}]\n[HẾT TƯ LIỆU HÌNH ẢNH]"
        except Exception as e:
            logger.warning(f"Failed to describe image via Gemini ({e}), falling back to Ollama Vision...")

    # 2. Xử lý qua Ollama Vision (qwen2.5-vl hoặc VLM self-host trên server)
    model_name = "qwen2.5vl:7b" if "gemini" in settings.VISION_MODEL.lower() else settings.VISION_MODEL
    payload = {
        "model": model_name,
        "messages": [
            {
                "role": "user",
                "content": prompt_text,
                "images": [raw_b64.strip()]
            }
        ],
        "stream": False
    }
    vision_user = settings.VISION_OLLAMA_USERNAME if settings.VISION_OLLAMA_USERNAME else settings.OLLAMA_USERNAME
    vision_pass = settings.VISION_OLLAMA_PASSWORD if settings.VISION_OLLAMA_PASSWORD else settings.OLLAMA_PASSWORD
    auth = (vision_user, vision_pass) if vision_user else None
    try:
        res = await ollama_client.post(_vision_ollama_url("/api/chat"), json=payload, auth=auth, timeout=180.0)
        res.raise_for_status()
        data = res.json()
        content = data.get("message", {}).get("content", "")
        if content:
            return content.strip()
        fallback_name = caption or 'Ảnh tư liệu'
        return f"[TƯ LIỆU HÌNH ẢNH: {fallback_name}]\n[HẾT TƯ LIỆU HÌNH ẢNH]"
    except Exception as e:
        err_msg = f"{type(e).__name__}: {str(e)}" if str(e) else type(e).__name__
        logger.warning(f"Ollama Vision ({model_name}) error: {err_msg}. Preserving original document caption.")
        fallback_name = caption or 'Ảnh tư liệu'
        return f"[TƯ LIỆU HÌNH ẢNH: {fallback_name}]\n- Chú giải tư liệu: {caption_info}\n[HẾT TƯ LIỆU HÌNH ẢNH]"
