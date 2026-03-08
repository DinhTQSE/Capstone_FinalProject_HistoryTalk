"""LangChain-based LLM service for character roleplay."""

from typing import List, Optional
from pydantic import BaseModel, Field

from langchain_core.messages import AIMessage, HumanMessage, SystemMessage, BaseMessage
from langchain_core.language_models import BaseChatModel

from app.config import settings
from app.models.character import CharacterData
from app.models.historical_context import HistoricalContextData
from app.models.chat import MessageHistoryItem
from app.services.prompt_builder import build_chat_system_prompt, build_title_system_prompt


# ── Structured output schemas ─────────────────────────────────────────────────

class _CharacterReply(BaseModel):
    """Structured output from the roleplay LLM call."""

    message: str = Field(description="Câu trả lời của nhân vật lịch sử")
    suggestedQuestions: List[str] = Field(
        default_factory=list,
        description=(
            "Danh sách 3 câu hỏi gợi ý mà người dùng có thể tiếp tục hỏi nhân vật. "
            "Mỗi câu hỏi ngắn gọn, liên quan đến chủ đề vừa thảo luận."
        ),
        max_length=3,
    )


class _SessionTitle(BaseModel):
    title: str = Field(description="Tiêu đề ngắn gọn dưới 8 từ tiếng Việt")


# ── LLM factory ──────────────────────────────────────────────────────────────

def _build_llm() -> BaseChatModel:
    """Build the configured LLM. Supports OpenAI and Google Gemini."""
    if settings.LLM_PROVIDER == "google":
        from langchain_google_genai import ChatGoogleGenerativeAI  # type: ignore

        return ChatGoogleGenerativeAI(
            model=settings.LLM_MODEL,
            temperature=settings.LLM_TEMPERATURE,
            max_output_tokens=settings.LLM_MAX_TOKENS,
            google_api_key=settings.GOOGLE_API_KEY,
        )

    # Default: OpenAI
    from langchain_openai import ChatOpenAI  # type: ignore

    return ChatOpenAI(
        model=settings.LLM_MODEL,
        temperature=settings.LLM_TEMPERATURE,
        max_tokens=settings.LLM_MAX_TOKENS,
        api_key=settings.OPENAI_API_KEY,
    )


# Build once at module import time so the model is reused across requests.
_llm = _build_llm()
_roleplay_llm = _llm.with_structured_output(_CharacterReply)
_title_llm = _llm.with_structured_output(_SessionTitle)


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

    messages: List[BaseMessage] = [SystemMessage(content=system_prompt)]

    # Inject conversation history
    for item in message_history:
        if item.role == "user":
            messages.append(HumanMessage(content=item.content))
        else:
            messages.append(AIMessage(content=item.content))

    # Append the new user turn
    messages.append(HumanMessage(content=user_message))

    result: _CharacterReply = await _roleplay_llm.ainvoke(messages)
    return result.message, result.suggestedQuestions[:3]


async def generate_session_title(
    character: CharacterData,
    first_user_message: str,
    first_assistant_message: str,
) -> str:
    """Generate a short session title from the first exchange."""
    system_prompt = build_title_system_prompt(character)
    conversation_snippet = (
        f"Người dùng: {first_user_message}\n"
        f"{character.name}: {first_assistant_message}"
    )
    messages: List[BaseMessage] = [
        SystemMessage(content=system_prompt),
        HumanMessage(content=conversation_snippet),
    ]
    result: _SessionTitle = await _title_llm.ainvoke(messages)
    return result.title
