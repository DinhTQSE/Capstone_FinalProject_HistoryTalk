import json
import logging
from typing import List
import httpx

from history_talk_ai.common.config.settings import settings
from history_talk_ai.dataaccess.java_backend.character_schema import CharacterData
from history_talk_ai.dataaccess.java_backend.historical_context_schema import HistoricalContextData
from history_talk_ai.presentation.chat.schemas import MessageHistoryItem
from history_talk_ai.application.prompting.prompt_builder import (
    build_chat_system_prompt,
    build_title_system_prompt,
)

logger = logging.getLogger(__name__)

# ── Ollama HTTP Client ────────────────────────────────────────────────────────

async def _call_ollama(messages: list[dict], expect_json: bool = True) -> str:
    """Make an async call to the Ollama endpoint."""
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

    async with httpx.AsyncClient(timeout=120.0) as client:
        try:
            response = await client.post(
                settings.OLLAMA_BASE_URL,
                json=payload,
                auth=auth
            )
            response.raise_for_status()
            data = response.json()
            return data.get("message", {}).get("content", "")
        except Exception as e:
            logger.error(f"Failed to call Ollama: {e}")
            raise

# ── Public service functions ──────────────────────────────────────────────────

async def generate_reply(
    character: CharacterData,
    context: HistoricalContextData,
    user_message: str,
    message_history: List[MessageHistoryItem],
) -> tuple[str, List[str]]:
    """
    Invoke the LLM in character-roleplay mode.

    Returns:
        (assistant_message, suggested_questions)
    """
    system_prompt = build_chat_system_prompt(character, context)
    
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

    response_text = await _call_ollama(messages, expect_json=True)
    
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
        return message, suggested_questions[:3]
    except Exception as e:
        logger.error(f"Failed to parse JSON from Ollama: {response_text}. Error: {e}")
        # Fallback if json parsing fails
        return response_text, []

async def generate_session_title(
    character: CharacterData,
    first_user_message: str,
    first_assistant_message: str,
) -> str:
    """Generate a short session title from the first exchange."""
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

    response_text = await _call_ollama(messages, expect_json=True)
    
    try:
        clean_text = response_text.strip()
        if clean_text.startswith("```json"):
            clean_text = clean_text[7:]
        if clean_text.endswith("```"):
            clean_text = clean_text[:-3]
            
        parsed = json.loads(clean_text.strip())
        return parsed.get("title", "Cuộc trò chuyện mới")
    except Exception as e:
        logger.error(f"Failed to parse JSON from Ollama title generation: {response_text}. Error: {e}")
        return "Cuộc trò chuyện mới"
