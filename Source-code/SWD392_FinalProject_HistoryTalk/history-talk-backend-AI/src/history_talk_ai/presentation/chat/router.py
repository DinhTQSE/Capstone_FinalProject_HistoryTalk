"""Chat router — all AI chat endpoints live here."""

import asyncio
import json
import logging
from fastapi import APIRouter, HTTPException, status
from fastapi.responses import StreamingResponse

from history_talk_ai.presentation.chat.schemas import (
    ChatRequest,
    ChatResponse,
    ChatResponseData,
    ExamGradeRequest,
    ExamGradeResponse,
    ExamGradeResponseData,
    ExamQuestionRequest,
    ExamQuestionResponse,
    ExamQuestionResponseData,
    GenerateTitleRequest,
    GenerateTitleResponse,
    GenerateTitleResponseData,
    ProcessDocumentRequest,
    TokenUsage,
    DescribeImagesRequest,
    DescribeImagesResponse,
    DescribedImageItem,
)
from history_talk_ai.dataaccess.java_backend.character_schema import CharacterData
from history_talk_ai.dataaccess.java_backend.historical_context_schema import HistoricalContextData
from history_talk_ai.dataaccess.java_backend import client as java_client
from history_talk_ai.application.chat import exam_service, service as llm_service
from history_talk_ai.dataaccess.java_backend.client import JavaClientNotFoundError, JavaBackendError

logger = logging.getLogger(__name__)

router = APIRouter(prefix="/v1/ai", tags=["AI Chat"])


async def _resolve_character_and_context(
    character_id: str,
    context_id: str | None,
    character_data: CharacterData | None,
    context_data: HistoricalContextData | None,
) -> tuple[CharacterData, HistoricalContextData | None]:
    """
    Return character + context data.
    Uses pre-fetched values when provided; otherwise fetches in parallel
    from the Java backend to minimise latency.
    """
    need_character = character_data is None
    
    # Only fetch context if context_id is provided, not empty, and not the fake UUID
    is_valid_context_id = (
        context_id is not None 
        and context_id.strip() != "" 
        and context_id != "00000000-0000-0000-0000-000000000000"
    )
    need_context = context_data is None and is_valid_context_id

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

    return character_data, context_data


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
        message, suggested_questions, prompt_tokens, completion_tokens, quotes_used = await llm_service.generate_reply(
            character=character,
            context=context,
            user_message=body.userMessage,
            message_history=body.messageHistory,
            skip_suggestions=body.skipSuggestions,
        )
    except llm_service.RagUnavailableError as exc:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exc))
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

    return ChatResponse(
        data=ChatResponseData(
            message=message,
            suggestedQuestions=suggested_questions,
            quotes_used=quotes_used,
            tokenUsage=token_usage,
        )
    )


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
                skip_suggestions=body.skipSuggestions,
            )
            async for chunk in stream:
                if isinstance(chunk, dict):
                    # Metadata chunk
                    yield f"data: {json.dumps({'type': 'metadata', 'data': chunk})}\n\n"
                else:
                    # Text chunk
                    yield f"data: {json.dumps({'type': 'text', 'data': chunk})}\n\n"
        except llm_service.RagUnavailableError as exc:
            yield f"data: {json.dumps({'type': 'error', 'message': str(exc)})}\n\n"
        except Exception as exc:
            # Raw errors can contain internal URLs, so the client only gets a generic message.
            logger.error(f"Chat stream failed: {exc}")
            yield f"data: {json.dumps({'type': 'error', 'message': 'Dịch vụ AI đang gặp sự cố. Vui lòng thử lại sau.'})}\n\n"
            
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


# ── POST /v1/ai/exam/question ────────────────────────────────────────────────

@router.post(
    "/exam/question",
    response_model=ExamQuestionResponse,
    summary="Generate the next oral-exam question for a character",
    description=(
        "Picks a source excerpt from the character's documents and asks a question answerable from it. "
        "The expected answer and excerpt are returned for grading and must not be shown to the learner."
    ),
)
async def exam_question(body: ExamQuestionRequest) -> ExamQuestionResponse:
    character, context = await _resolve_character_and_context(
        body.characterId,
        body.contextId,
        body.characterData,
        body.contextData,
    )

    try:
        question, expected_answer, source, prompt_tokens, completion_tokens = await exam_service.generate_exam_question(
            character=character,
            context=context,
            asked_questions=body.askedQuestions,
            used_sources=body.usedSources,
        )
    except exam_service.ExamSourceUnavailableError as exc:
        raise HTTPException(status_code=status.HTTP_422_UNPROCESSABLE_ENTITY, detail=str(exc))
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"LLM error: {exc}",
        )

    return ExamQuestionResponse(
        data=ExamQuestionResponseData(
            question=question,
            expectedAnswer=expected_answer,
            sourceExcerpt=source,
            tokenUsage=TokenUsage(
                promptTokens=prompt_tokens,
                completionTokens=completion_tokens,
                totalTokens=prompt_tokens + completion_tokens,
            ),
        )
    )


# ── POST /v1/ai/exam/grade ───────────────────────────────────────────────────

@router.post(
    "/exam/grade",
    response_model=ExamGradeResponse,
    summary="Grade a learner's oral-exam answer against the source excerpt",
)
async def exam_grade(body: ExamGradeRequest) -> ExamGradeResponse:
    character, _ = await _resolve_character_and_context(
        body.characterId,
        body.contextId,
        body.characterData,
        body.contextData,
    )

    try:
        verdict, feedback, prompt_tokens, completion_tokens = await exam_service.grade_exam_answer(
            character=character,
            question=body.question,
            expected_answer=body.expectedAnswer,
            source=body.sourceExcerpt,
            answer=body.answer,
        )
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"LLM error: {exc}",
        )

    return ExamGradeResponse(
        data=ExamGradeResponseData(
            verdict=verdict,
            feedback=feedback,
            tokenUsage=TokenUsage(
                promptTokens=prompt_tokens,
                completionTokens=completion_tokens,
                totalTokens=prompt_tokens + completion_tokens,
            ),
        )
    )


# ── POST /v1/ai/documents/process ───────────────────────────────────────────

@router.post(
    "/documents/process",
    summary="Process and index a document into VectorChunk (Supabase)",
    description=(
        "Chunk the given document content, generate embeddings via Ollama (bge-m3), "
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


# ── POST /v1/ai/documents/describe-images ────────────────────────────────────

@router.post(
    "/documents/describe-images",
    response_model=DescribeImagesResponse,
    summary="Analyze historical images and battle maps using Vision Model (Gemini / Qwen-VL)",
    description=(
        "Accepts a list of base64 images extracted from historical textbook pages, "
        "runs them through Vision AI, and returns rich textual context descriptions."
    ),
)
async def describe_images(body: DescribeImagesRequest):
    try:
        results = []
        for item in body.images:
            desc = await llm_service.describe_historical_image(
                image_base64=item.image_base64,
                caption=item.caption
            )
            results.append(
                DescribedImageItem(
                    page_number=item.page_number,
                    caption=item.caption,
                    description_block=desc
                )
            )
        return DescribeImagesResponse(success=True, data=results)
    except Exception as exc:
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail=f"Image description error: {exc}",
        )
