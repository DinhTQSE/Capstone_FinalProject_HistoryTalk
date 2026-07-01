import json
import uuid
import logging
import asyncio
from typing import List
import httpx
from supabase import create_client, Client

from history_talk_ai.common.config.settings import settings
from history_talk_ai.dataaccess.java_backend.character_schema import CharacterData
from history_talk_ai.dataaccess.java_backend.historical_context_schema import HistoricalContextData
from history_talk_ai.presentation.chat.schemas import MessageHistoryItem, ProcessDocumentRequest
from history_talk_ai.application.prompting.prompt_builder import (
    build_chat_system_prompt,
    build_title_system_prompt,
)

logger = logging.getLogger(__name__)

# ── Supabase Client ───────────────────────────────────────────────────────────
supabase: Client = create_client(settings.SUPABASE_URL, settings.SUPABASE_KEY)

# ── Ollama HTTP Client ────────────────────────────────────────────────────────
# Use a global client to reuse connections (Connection Pooling) for faster requests
ollama_client = httpx.AsyncClient(timeout=120.0, follow_redirects=True)


def _ollama_url(path: str) -> str:
    return f"{settings.OLLAMA_BASE_URL.rstrip('/')}{path}"

async def _call_ollama(messages: list[dict], expect_json: bool = True) -> tuple[str, int, int]:
    """Make an async call to the Ollama endpoint. Returns (content, prompt_tokens, completion_tokens)"""
    payload = {
        "model": settings.LLM_MODEL,
        "messages": messages,
        "stream": False,
        "options": {
            "temperature": settings.LLM_TEMPERATURE,
            "num_predict": settings.LLM_MAX_TOKENS,
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
        "options": {
            "temperature": settings.LLM_TEMPERATURE,
            "num_predict": settings.LLM_MAX_TOKENS,
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
            timeout=120.0
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
            response.raise_for_status()
            data = response.json()
            embedding = data.get("embedding", [])
            if embedding:
                return embedding
        except Exception as e:
            logger.warning(f"Ollama embedding attempt {attempt + 1}/{max_retries} failed: {e}")
            if attempt < max_retries - 1:
                await asyncio.sleep(1.0 * (attempt + 1))  # Exponential backoff
                
    logger.error("All attempts to get embedding from Ollama failed.")
    return []

async def retrieve_history_context(user_question: str, entity_ids: List[str]) -> str:
    """Retrieve history context from Supabase VectorChunk."""
    query_vector = await get_embedding_from_ollama(user_question)
    if not query_vector:
        return ""
        
    try:
        response = supabase.rpc(
            "match_history_chunks",
            {
                "query_embedding": query_vector,
                "match_limit": 5,
                "filter_entity_ids": entity_ids
            }
        ).execute()
        
        chunks = response.data
        print(f"RAG retrieved {len(chunks)} chunks for entity_ids: {entity_ids}")
        logger.info(f"RAG retrieved {len(chunks)} chunks for entity_ids: {entity_ids}")
        if not chunks:
            return ""
        texts = [chunk.get("content", "") for chunk in chunks if chunk.get("content")]
        
        # Log the actual RAG content
        print("\n" + "="*50)
        print("🔍 RAG RETRIEVED CONTENT:")
        for i, text in enumerate(texts):
            print(f"--- Chunk {i+1} ---")
            print(text)
        print("="*50 + "\n")
        logger.info(f"RAG Context length: {sum(len(t) for t in texts)} characters")
        
        return "\n\n".join(texts)
    except Exception as e:
        logger.error(f"Supabase RPC error: {e}")
        return ""


# ── Public service functions ──────────────────────────────────────────────────

async def generate_reply(
    character: CharacterData,
    context: HistoricalContextData | None,
    user_message: str,
    message_history: List[MessageHistoryItem],
    skip_suggestions: bool = False,
) -> tuple[str, List[str], int, int]:
    """
    Invoke the LLM in character-roleplay mode.

    Returns:
        (assistant_message, suggested_questions, prompt_tokens, completion_tokens)
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
    if message_history:
        rag_context = await retrieve_history_context(user_message, entity_ids)
    
    system_prompt = build_chat_system_prompt(character, context)
    
    if rag_context:
        print(f"--- RAG CONTEXT INJECTED ---\n{rag_context}\n----------------------------")
        system_prompt += (
            f"\n\n[DỮ LIỆU THAM KHẢO]:\n{rag_context}\n\n"
            "LỆNH RAG:\n"
            "1. Ưu tiên tuyệt đối DỮ LIỆU THAM KHẢO, kể cả khi mâu thuẫn kiến thức cũ.\n"
            "2. Bám sát dữ liệu, KHÔNG tự suy diễn thêm (vd: truyền thuyết, thần tiên).\n"
            "3. Nếu dữ liệu không đề cập, phải thừa nhận không biết."
        )
    
    # No JSON instruction for the main message anymore, to keep roleplay pure.

    messages = [{"role": "system", "content": system_prompt}]

    # Inject conversation history
    for item in message_history:
        messages.append({"role": item.role, "content": item.content})

    # Append the new user turn
    messages.append({"role": "user", "content": user_message})

    response_text, prompt_tokens, completion_tokens = await _call_ollama(messages, expect_json=False)
    
    suggested_questions = []
    sq_pt = 0
    sq_ct = 0
    
    if not skip_suggestions:
        sq_prompt = (
            "Dựa vào câu trả lời vừa rồi, hãy gợi ý 3 câu hỏi (dưới 10 từ) để người dùng hỏi tiếp.\n"
            "BẮT BUỘC 100% TIẾNG VIỆT. TUYỆT ĐỐI KHÔNG DÙNG TIẾNG TRUNG QUỐC.\n"
            "CHỈ TRẢ VỀ ĐÚNG 1 ĐOẠN JSON NHƯ SAU:\n"
            '{"suggestedQuestions": ["câu hỏi 1", "câu hỏi 2", "câu hỏi 3"]}'
        )
        sq_messages = messages + [
            {"role": "assistant", "content": response_text},
            {"role": "user", "content": sq_prompt}
        ]
        
        sq_text, sq_pt, sq_ct = await _call_ollama(sq_messages, expect_json=True)
        try:
            clean_text = sq_text.strip()
            if clean_text.startswith("```json"): clean_text = clean_text[7:]
            if clean_text.endswith("```"): clean_text = clean_text[:-3]
            parsed = json.loads(clean_text.strip())
            suggested_questions = parsed.get("suggestedQuestions", [])[:3]
        except Exception as e:
            logger.error(f"Failed to parse suggested questions JSON: {sq_text}. Error: {e}")

    return response_text.strip(), suggested_questions, prompt_tokens + sq_pt, completion_tokens + sq_ct

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
    if message_history:
        rag_context = await retrieve_history_context(user_message, entity_ids)
    
    system_prompt = build_chat_system_prompt(character, context)
    
    if rag_context:
        system_prompt += (
            f"\n\n[DỮ LIỆU THAM KHẢO]:\n{rag_context}\n\n"
            "LỆNH RAG:\n"
            "1. Ưu tiên tuyệt đối DỮ LIỆU THAM KHẢO, kể cả khi mâu thuẫn kiến thức cũ.\n"
            "2. Bám sát dữ liệu, KHÔNG tự suy diễn thêm (vd: truyền thuyết, thần tiên).\n"
            "3. Nếu dữ liệu không đề cập, phải thừa nhận không biết."
        )
    
    # We do NOT force JSON for streaming because streaming JSON is hard to parse continuously.
    messages = [{"role": "system", "content": system_prompt}]
    for item in message_history:
        messages.append({"role": item.role, "content": item.content})
    messages.append({"role": "user", "content": user_message})

    full_message = ""
    prompt_tokens = 0
    completion_tokens = 0

    # Stream the message
    stream_gen = _call_ollama_stream(messages)
    async for chunk in stream_gen:
        if isinstance(chunk, dict):
            prompt_tokens = chunk.get("prompt_tokens", 0)
            completion_tokens = chunk.get("completion_tokens", 0)
        else:
            full_message += chunk
            await asyncio.sleep(0.05)  # SLOW DOWN YIELD SPEED (50ms per chunk)
            yield chunk
            
    suggested_questions = []
    sq_pt = 0
    sq_ct = 0

    if not skip_suggestions:
        # Now that the message is done, generate suggested questions synchronously but fast
        # By asking specifically for 3 suggested questions based on the last reply.
        sq_prompt = (
            "Dựa vào câu trả lời vừa rồi, hãy gợi ý 3 câu hỏi (dưới 10 từ) để người dùng hỏi tiếp.\n"
            "BẮT BUỘC 100% TIẾNG VIỆT. TUYỆT ĐỐI KHÔNG DÙNG TIẾNG TRUNG QUỐC.\n"
            "CHỈ TRẢ VỀ ĐÚNG 1 ĐOẠN JSON NHƯ SAU:\n"
            '{"suggestedQuestions": ["câu hỏi 1", "câu hỏi 2", "câu hỏi 3"]}'
        )
        sq_messages = messages + [
            {"role": "assistant", "content": full_message},
            {"role": "user", "content": sq_prompt}
        ]
        
        sq_text, sq_pt, sq_ct = await _call_ollama(sq_messages, expect_json=True)
        try:
            clean_text = sq_text.strip()
            if clean_text.startswith("```json"): clean_text = clean_text[7:]
            if clean_text.endswith("```"): clean_text = clean_text[:-3]
            parsed = json.loads(clean_text.strip())
            suggested_questions = parsed.get("suggestedQuestions", [])[:3]
        except Exception as e:
            logger.error(f"Failed to parse suggested questions JSON: {sq_text}. Error: {e}")

    yield {
        "suggestedQuestions": suggested_questions,
        "promptTokens": prompt_tokens + sq_pt,
        "completionTokens": completion_tokens + sq_ct
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

    response_text, prompt_tokens, completion_tokens = await _call_ollama(messages, expect_json=False)
    
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

    content = request.content
    chunk_size = 600
    overlap = 150
    chunks = []
    
    if len(content) <= chunk_size:
        chunks = [content]
    else:
        start = 0
        while start < len(content):
            end = start + chunk_size
            if end >= len(content):
                chunks.append(content[start:])
                break
            
            # Find the last period, newline, or space to avoid cutting words
            last_period = content.rfind('.', start, end)
            last_newline = content.rfind('\n', start, end)
            last_space = content.rfind(' ', start, end)
            
            # Prefer splitting at a period or newline, otherwise space
            split_at = max(last_period, last_newline)
            if split_at <= start + chunk_size // 2: # If no good punctuation in the second half, fallback to space
                split_at = last_space
                
            if split_at <= start: # Fallback if no space at all
                split_at = end
            else:
                split_at += 1 # Include the space or punctuation in the current chunk
                
            chunks.append(content[start:split_at].strip())
            
            # Determine the start of the next chunk (overlap)
            next_start = split_at - overlap
            if next_start > start:
                # Try to find a period to start the next chunk cleanly
                period_after = content.find('.', next_start, split_at)
                if period_after != -1 and period_after < split_at - 20:
                    start = period_after + 1
                else:
                    # Fallback to the next space
                    space_after = content.find(' ', next_start, split_at)
                    if space_after != -1:
                        start = space_after + 1
                    else:
                        start = next_start
            else:
                start = split_at
                
            # Strip leading spaces for the next chunk's start
            while start < len(content) and content[start].isspace():
                start += 1
    
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
