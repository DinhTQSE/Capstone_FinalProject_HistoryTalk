"""Request and response models for the AI chat API."""

from typing import List, Literal, Optional
from pydantic import BaseModel, Field, field_validator

from app.models.character import CharacterData
from app.models.historical_context import HistoricalContextData


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
    contextId: str = Field(..., description="UUID of the historical context")
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


class GenerateTitleRequest(BaseModel):
    characterId: str
    contextId: str
    firstUserMessage: str
    firstAssistantMessage: str
    characterData: Optional[CharacterData] = None
    contextData: Optional[HistoricalContextData] = None


# ── Response ──────────────────────────────────────────────────────────────────

class ChatResponseData(BaseModel):
    message: str
    suggestedQuestions: List[str] = Field(default_factory=list)


class ChatResponse(BaseModel):
    success: bool = True
    data: ChatResponseData


class GenerateTitleResponseData(BaseModel):
    title: str


class GenerateTitleResponse(BaseModel):
    success: bool = True
    data: GenerateTitleResponseData


class ErrorResponse(BaseModel):
    success: bool = False
    errorCode: str
    message: str
