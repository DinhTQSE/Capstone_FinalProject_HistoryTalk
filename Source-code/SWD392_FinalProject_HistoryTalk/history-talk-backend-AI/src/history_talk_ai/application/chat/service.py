import json
import uuid
import logging
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
            settings.OLLAMA_BASE_URL,
            json=payload,
            auth=auth
        )
        response.raise_for_status()
        data = response.json()
        content = data.get("message", {}).get("content", "")
        prompt_tokens = data.get("prompt_eval_count", 0)
        completion_tokens = data.get("eval_count", 0)
        return content, prompt_tokens, completion_tokens
    except Exception as e:
        logger.error(f"Failed to call Ollama: {e}")
        raise

async def get_embedding_from_ollama(text: str) -> List[float]:
    """Get embedding vector for a given text from Ollama."""
    url = settings.OLLAMA_BASE_URL.replace("/api/chat", "/api/embeddings")
    payload = {
        "model": "nomic-embed-text",
        "prompt": text
    }
    auth = (settings.OLLAMA_USERNAME, settings.OLLAMA_PASSWORD) if settings.OLLAMA_USERNAME else None

    try:
        response = await ollama_client.post(url, json=payload, auth=auth)
        response.raise_for_status()
        data = response.json()
        return data.get("embedding", [])
    except Exception as e:
        logger.error(f"Failed to get embedding from Ollama: {e}")
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
        return "\n\n".join(texts)
    except Exception as e:
        logger.error(f"Supabase RPC error: {e}")
        return ""


# ── Public service functions ──────────────────────────────────────────────────

async def generate_reply(
    character: CharacterData,
    context: HistoricalContextData,
    user_message: str,
    message_history: List[MessageHistoryItem],
) -> tuple[str, List[str], int, int]:
    """
    Invoke the LLM in character-roleplay mode.

    Returns:
        (assistant_message, suggested_questions, prompt_tokens, completion_tokens)
    """
    # ── RAG Integration ──
    # Query both context documents and character documents
    entity_ids = [context.contextId, character.characterId]
    rag_context = await retrieve_history_context(user_message, entity_ids)
    
    system_prompt = build_chat_system_prompt(character, context)
    
    if rag_context:
        print(f"--- RAG CONTEXT INJECTED ---\n{rag_context}\n----------------------------")
        system_prompt += (
            f"\n\n[DỮ LIỆU LỊCH SỬ THAM KHẢO (BẮT BUỘC TUÂN THEO)]:\n{rag_context}\n\n"
            "LỆNH TUYỆT ĐỐI TỐI CAO:\n"
            "1. BẠN PHẢI COI CÁC THÔNG TIN TRONG [DỮ LIỆU LỊCH SỬ THAM KHẢO] LÀ SỰ THẬT LỊCH SỬ CHÍNH XÁC NHẤT (DÙ NÓ CÓ VÔ LÝ HAY SAI LỆCH VỚI KIẾN THỨC CỦA BẠN).\n"
            "2. NẾU DỮ LIỆU THAM KHẢO KHÔNG NHẮC ĐẾN CHI TIẾT ĐÓ, TUYỆT ĐỐI KHÔNG ĐƯỢC TỰ BỊA RA HOẶC TỰ SUY DIỄN THÊM (ví dụ: tự sáng tác ra nguồn gốc thanh kiếm, thần tiên, truyền thuyết, v.v.).\n"
            "3. HÃY THẲNG THẮN THỪA NHẬN RẰNG BẠN KHÔNG NHỚ, KHÔNG RÕ HOẶC LỊCH SỬ KHÔNG GHI CHÉP LẠI NẾU KHÔNG CÓ THÔNG TIN."
        )
    
    # Append instructions to force JSON output
    json_instruction = (
        "\n\nBẠN BẮT BUỘC PHẢI TRẢ VỀ KẾT QUẢ DƯỚI ĐỊNH DẠNG JSON. KHÔNG KÈM THEO BẤT KỲ VĂN BẢN NÀO BÊN NGOÀI JSON.\n"
        "Cấu trúc JSON yêu cầu:\n"
        "{\n"
        '  "message": "Câu trả lời của bạn",\n'
        '  "suggestedQuestions": ["Câu hỏi 1", "Câu hỏi 2", "Câu hỏi 3"]\n'
        "}"
    )
    system_prompt += json_instruction

    messages = [{"role": "system", "content": system_prompt}]

    # Inject conversation history
    for item in message_history:
        messages.append({"role": item.role, "content": item.content})

    # Append the new user turn
    messages.append({"role": "user", "content": user_message})

    response_text, prompt_tokens, completion_tokens = await _call_ollama(messages, expect_json=True)
    
    try:
        # Sometimes models wrap json in markdown fences
        clean_text = response_text.strip()
        if clean_text.startswith("```json"):
            clean_text = clean_text[7:]
        if clean_text.endswith("```"):
            clean_text = clean_text[:-3]
        
        parsed = json.loads(clean_text.strip())
        message = parsed.get("message", "")
        suggested_questions = parsed.get("suggestedQuestions", [])
        return message, suggested_questions[:3], prompt_tokens, completion_tokens
    except Exception as e:
        logger.error(f"Failed to parse JSON from Ollama: {response_text}. Error: {e}")
        # Fallback if json parsing fails
        return response_text, [], prompt_tokens, completion_tokens

async def generate_session_title(
    character: CharacterData,
    first_user_message: str,
    first_assistant_message: str,
) -> tuple[str, int, int]:
    """Generate a short session title from the first exchange. Returns (title, prompt_tokens, completion_tokens)"""
    system_prompt = build_title_system_prompt(character)
    
    json_instruction = (
        "\n\nBẠN BẮT BUỘC PHẢI TRẢ VỀ KẾT QUẢ DƯỚI ĐỊNH DẠNG JSON. KHÔNG KÈM THEO BẤT KỲ VĂN BẢN NÀO BÊN NGOÀI JSON.\n"
        "Cấu trúc JSON yêu cầu:\n"
        "{\n"
        '  "title": "Tiêu đề ngắn gọn dưới 8 từ tiếng Việt"\n'
        "}"
    )
    system_prompt += json_instruction
    
    conversation_snippet = (
        f"Người dùng: {first_user_message}\n"
        f"{character.name}: {first_assistant_message}"
    )
    
    messages = [
        {"role": "system", "content": system_prompt},
        {"role": "user", "content": conversation_snippet}
    ]

    response_text, prompt_tokens, completion_tokens = await _call_ollama(messages, expect_json=True)
    
    try:
        clean_text = response_text.strip()
        if clean_text.startswith("```json"):
            clean_text = clean_text[7:]
        if clean_text.endswith("```"):
            clean_text = clean_text[:-3]
            
        parsed = json.loads(clean_text.strip())
        return parsed.get("title", "Cuộc trò chuyện mới"), prompt_tokens, completion_tokens
    except Exception as e:
        logger.error(f"Failed to parse JSON from Ollama title generation: {response_text}. Error: {e}")
        return "Cuộc trò chuyện mới", prompt_tokens, completion_tokens

async def process_document(request: ProcessDocumentRequest):
    """Process a document by chunking, embedding, and storing in Supabase."""
    # Delete old chunks for this doc_id to handle upserts properly
    try:
        supabase.schema("historical_schema").table("vector_chunk").delete().eq("doc_id", request.doc_id).execute()
    except Exception as e:
        logger.error(f"Failed to delete old chunks for doc {request.doc_id}: {e}")

    # Chunking logic (overlap to prevent cutting sentences in half)
    content = request.content
    chunk_size = 600
    overlap = 150
    chunks = []
    
    if len(content) <= chunk_size:
        chunks = [content]
    else:
        for i in range(0, len(content), chunk_size - overlap):
            chunks.append(content[i:i+chunk_size])
            if i + chunk_size >= len(content):
                break
    
    for idx, chunk_text in enumerate(chunks):
        embedding = await get_embedding_from_ollama(chunk_text)
        if not embedding:
            logger.warning(f"Failed to generate embedding for chunk {idx} of document {request.doc_id}")
            continue
            
        try:
            record = {
                "chunk_id": str(uuid.uuid4()),
                "doc_id": request.doc_id,
                "entity_id": request.entity_id,
                "content": chunk_text,
                "embedding": embedding,
                "sequence_number": idx + 1
            }
            supabase.schema("historical_schema").table("vector_chunk").insert(record).execute()
        except Exception as e:
            logger.error(f"Failed to insert chunk {idx} into Supabase: {e}")
            raise

async def delete_document(doc_id: str):
    """Delete all chunks for a document from Supabase."""
    try:
        supabase.schema("historical_schema").table("vector_chunk").delete().eq("doc_id", doc_id).execute()
        logger.info(f"Successfully deleted chunks for doc {doc_id}")
    except Exception as e:
        logger.error(f"Failed to delete chunks for doc {doc_id}: {e}")
        raise
