"""Request and response models for the AI chat API."""

from typing import List, Literal, Optional
from pydantic import BaseModel, Field, field_validator

from history_talk_ai.dataaccess.java_backend.character_schema import CharacterData
from history_talk_ai.dataaccess.java_backend.historical_context_schema import HistoricalContextData


# ── Request ───────────────────────────────────────────────────────────────────

class MessageHistoryItem(BaseModel):
    """A single turn in the conversation history.

    Java's MessageRole enum serializes as "USER" / "ASSISTANT" (uppercase).
    We normalize to lowercase so LangChain mapping works regardless of source.
    """

    role: Literal["user", "assistant"]
    content: str

    @field_validator("role", mode="before")
    @classmethod
    def normalize_role(cls, v: str) -> str:
        return v.lower()


class ChatRequest(BaseModel):
    characterId: str = Field(..., description="UUID of the character to roleplay as")
    contextId: Optional[str] = Field(None, description="UUID of the historical context")
    userMessage: str = Field(..., min_length=1, max_length=4000, description="The user's message")
    messageHistory: List[MessageHistoryItem] = Field(
        default_factory=list,
        description="Previous messages in the conversation (oldest first)",
    )
    # Optional pre-fetched data — callers that already have the data can skip
    # the extra round-trips to the Java backend.
    characterData: Optional[CharacterData] = Field(
        default=None,
        description="Pre-fetched character data (skips internal Java backend call if provided)",
    )
    contextData: Optional[HistoricalContextData] = Field(
        default=None,
        description="Pre-fetched context data (skips internal Java backend call if provided)",
    )
    skipSuggestions: bool = Field(
        default=False,
        description="If True, skips generating suggested questions to save tokens and latency",
    )


class GenerateTitleRequest(BaseModel):
    characterId: str
    contextId: Optional[str] = None
    firstUserMessage: str
    firstAssistantMessage: str
    characterData: Optional[CharacterData] = None
    contextData: Optional[HistoricalContextData] = None

class ProcessDocumentRequest(BaseModel):
    doc_id: str = Field(..., description="UUID của tài liệu")
    entity_id: str = Field(..., description="UUID của nhân vật hoặc bối cảnh")
    content: str = Field(..., description="Nội dung toàn bộ tài liệu")


# ── Response ──────────────────────────────────────────────────────────────────

class TokenUsage(BaseModel):
    provider: str = "ollama"
    model: str = "qwen2.5:14b"
    promptTokens: int = 0
    completionTokens: int = 0
    totalTokens: int = 0


class ChatResponseData(BaseModel):
    message: str
    suggestedQuestions: List[str] = Field(default_factory=list)
    tokenUsage: TokenUsage = Field(default_factory=TokenUsage)


class ChatResponse(BaseModel):
    success: bool = True
    data: ChatResponseData


class GenerateTitleResponseData(BaseModel):
    title: str
    tokenUsage: TokenUsage = Field(default_factory=TokenUsage)


class GenerateTitleResponse(BaseModel):
    success: bool = True
    data: GenerateTitleResponseData


class ErrorResponse(BaseModel):
    success: bool = False
    errorCode: str
    message: str
