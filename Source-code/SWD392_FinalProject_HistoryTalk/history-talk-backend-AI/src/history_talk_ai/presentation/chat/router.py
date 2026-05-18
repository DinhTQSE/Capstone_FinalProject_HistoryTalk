"""Chat router — all AI chat endpoints live here."""

import asyncio
from fastapi import APIRouter, HTTPException, status

from app.models.chat import (
    ChatRequest,
    ChatResponse,
    ChatResponseData,
    GenerateTitleRequest,
    GenerateTitleResponse,
    GenerateTitleResponseData,
)
from app.models.character import CharacterData
from app.models.historical_context import HistoricalContextData
from app.services import java_client, llm_service
from app.services.java_client import JavaClientNotFoundError, JavaBackendError

router = APIRouter(prefix="/v1/ai", tags=["AI Chat"])


async def _resolve_character_and_context(
    character_id: str,
    context_id: str,
    character_data: CharacterData | None,
    context_data: HistoricalContextData | None,
) -> tuple[CharacterData, HistoricalContextData]:
    """
    Return character + context data.
    Uses pre-fetched values when provided; otherwise fetches in parallel
    from the Java backend to minimise latency.
    """
    need_character = character_data is None
    need_context = context_data is None

    if need_character and need_context:
        try:
            character_data, context_data = await asyncio.gather(
                java_client.get_character(character_id),
                java_client.get_historical_context(context_id),
            )
        except JavaClientNotFoundError as exc:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=exc.message)
        except JavaBackendError as exc:
            raise HTTPException(
                status_code=status.HTTP_502_BAD_GATEWAY,
                detail=f"Java backend error: {exc.message}",
            )
    elif need_character:
        try:
            character_data = await java_client.get_character(character_id)
        except JavaClientNotFoundError as exc:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=exc.message)
        except JavaBackendError as exc:
            raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=exc.message)
    elif need_context:
        try:
            context_data = await java_client.get_historical_context(context_id)
        except JavaClientNotFoundError as exc:
            raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=exc.message)
        except JavaBackendError as exc:
            raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=exc.message)

    return character_data, context_data  # type: ignore[return-value]


# ── POST /v1/ai/chat ──────────────────────────────────────────────────────────

@router.post(
    "/chat",
    response_model=ChatResponse,
    summary="Send a message and receive an AI character response",
    description=(
        "Accepts a user message together with conversation history. "
        "Internally fetches the character and historical-context data from the Java backend "
        "(unless pre-provided), builds a roleplay system prompt, and invokes the LLM via LangChain."
    ),
)
async def chat(body: ChatRequest) -> ChatResponse:
    character, context = await _resolve_character_and_context(
        body.characterId,
        body.contextId,
        body.characterData,
        body.contextData,
    )

    try:
        message, suggested_questions = await llm_service.generate_reply(
            character=character,
            context=context,
            user_message=body.userMessage,
            message_history=body.messageHistory,
        )
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"LLM error: {exc}",
        )

    return ChatResponse(data=ChatResponseData(message=message, suggestedQuestions=suggested_questions))


# ── POST /v1/ai/generate-title ────────────────────────────────────────────────

@router.post(
    "/generate-title",
    response_model=GenerateTitleResponse,
    summary="Generate a short session title from the first exchange",
    description=(
        "Call this after the first user+assistant message pair to auto-generate "
        "a human-readable session title (≤ 8 Vietnamese words)."
    ),
)
async def generate_title(body: GenerateTitleRequest) -> GenerateTitleResponse:
    character, _ = await _resolve_character_and_context(
        body.characterId,
        body.contextId,
        body.characterData,
        body.contextData,
    )

    try:
        title = await llm_service.generate_session_title(
            character=character,
            first_user_message=body.firstUserMessage,
            first_assistant_message=body.firstAssistantMessage,
        )
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"LLM error: {exc}",
        )

    return GenerateTitleResponse(data=GenerateTitleResponseData(title=title))


# ── GET /v1/ai/character/{id} — diagnostic ────────────────────────────────────

@router.get(
    "/character/{character_id}",
    summary="[Diagnostic] Fetch character from Java backend",
    description="Proxy a single character fetch from the Java backend. Useful for verifying connectivity.",
)
async def proxy_character(character_id: str):
    try:
        return await java_client.get_character(character_id)
    except JavaClientNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=exc.message)
    except JavaBackendError as exc:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=exc.message)


# ── GET /v1/ai/context/{id} — diagnostic ─────────────────────────────────────

@router.get(
    "/context/{context_id}",
    summary="[Diagnostic] Fetch historical context from Java backend",
    description="Proxy a single context fetch from the Java backend. Useful for verifying connectivity.",
)
async def proxy_context(context_id: str):
    try:
        return await java_client.get_historical_context(context_id)
    except JavaClientNotFoundError as exc:
        raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail=exc.message)
    except JavaBackendError as exc:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=exc.message)
