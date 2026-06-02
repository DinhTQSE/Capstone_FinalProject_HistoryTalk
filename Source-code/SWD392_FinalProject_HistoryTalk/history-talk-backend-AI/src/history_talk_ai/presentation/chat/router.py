"""Chat router — all AI chat endpoints live here."""

import asyncio
import json
from fastapi import APIRouter, HTTPException, status
from fastapi.responses import StreamingResponse

from history_talk_ai.presentation.chat.schemas import (
    ChatRequest,
    ChatResponse,
    ChatResponseData,
    GenerateTitleRequest,
    GenerateTitleResponse,
    GenerateTitleResponseData,
    ProcessDocumentRequest,
    TokenUsage,
)
from history_talk_ai.dataaccess.java_backend.character_schema import CharacterData
from history_talk_ai.dataaccess.java_backend.historical_context_schema import HistoricalContextData
from history_talk_ai.dataaccess.java_backend import client as java_client
from history_talk_ai.application.chat import service as llm_service
from history_talk_ai.dataaccess.java_backend.client import JavaClientNotFoundError, JavaBackendError

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
        "(unless pre-provided), builds a roleplay system prompt with RAG context, and invokes the LLM."
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
        message, suggested_questions, prompt_tokens, completion_tokens = await llm_service.generate_reply(
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
    
    token_usage = TokenUsage(
        promptTokens=prompt_tokens,
        completionTokens=completion_tokens,
        totalTokens=prompt_tokens + completion_tokens
    )

    return ChatResponse(data=ChatResponseData(message=message, suggestedQuestions=suggested_questions, tokenUsage=token_usage))


@router.post(
    "/chat/stream",
    summary="Send a message and receive an AI character response as a stream",
    description="Same as /chat but streams the response using Server-Sent Events (SSE).",
)
async def chat_stream(body: ChatRequest):
    character, context = await _resolve_character_and_context(
        body.characterId,
        body.contextId,
        body.characterData,
        body.contextData,
    )

    async def event_generator():
        try:
            stream = llm_service.generate_reply_stream(
                character=character,
                context=context,
                user_message=body.userMessage,
                message_history=body.messageHistory,
            )
            async for chunk in stream:
                if isinstance(chunk, dict):
                    # Metadata chunk
                    yield f"data: {json.dumps({'type': 'metadata', 'data': chunk})}\n\n"
                else:
                    # Text chunk
                    yield f"data: {json.dumps({'type': 'text', 'data': chunk})}\n\n"
        except Exception as exc:
            yield f"data: {json.dumps({'type': 'error', 'message': str(exc)})}\n\n"
            
    return StreamingResponse(event_generator(), media_type="text/event-stream")


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
        title, prompt_tokens, completion_tokens = await llm_service.generate_session_title(
            character=character,
            first_user_message=body.firstUserMessage,
            first_assistant_message=body.firstAssistantMessage,
        )
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"LLM error: {exc}",
        )

    token_usage = TokenUsage(
        promptTokens=prompt_tokens,
        completionTokens=completion_tokens,
        totalTokens=prompt_tokens + completion_tokens
    )

    return GenerateTitleResponse(data=GenerateTitleResponseData(title=title, tokenUsage=token_usage))


# ── POST /v1/ai/documents/process ───────────────────────────────────────────

@router.post(
    "/documents/process",
    summary="Process and index a document into VectorChunk (Supabase)",
    description=(
        "Chunk the given document content, generate embeddings via Ollama (nomic-embed-text), "
        "and store the resulting VectorChunks in Supabase for future RAG retrieval."
    ),
)
async def process_document(body: ProcessDocumentRequest):
    try:
        await llm_service.process_document(body)
        return {"success": True, "message": f"Document {body.doc_id} processed successfully."}
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Document processing error: {exc}",
        )


@router.delete(
    "/documents/{doc_id}",
    summary="Delete a document's chunks from VectorChunk (Supabase)",
    description="Deletes all vector chunks associated with the given document ID.",
)
async def delete_document(doc_id: str):
    try:
        await llm_service.delete_document(doc_id)
        return {"success": True, "message": f"Document {doc_id} chunks deleted successfully."}
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Document deletion error: {exc}",
        )

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
