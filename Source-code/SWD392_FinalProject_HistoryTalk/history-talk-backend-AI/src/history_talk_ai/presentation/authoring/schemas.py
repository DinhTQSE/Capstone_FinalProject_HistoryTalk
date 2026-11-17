"""Request bodies of the authoring endpoints. The Java backend fills contextData and characterData."""

from typing import List, Literal, Optional

from pydantic import BaseModel, ConfigDict, Field

from history_talk_ai.dataaccess.java_backend.character_schema import CharacterData
from history_talk_ai.dataaccess.java_backend.historical_context_schema import HistoricalContextData


class Background(BaseModel):
    id: str
    name: str


class StoryOptions(BaseModel):
    gradeLevel: str = "THCS"
    targetMinutes: int = Field(3, ge=2, le=6)
    choiceCount: int = Field(1, ge=0, le=3)
    quizCount: int = Field(1, ge=0, le=3)
    tone: Optional[str] = Field(None, max_length=200)
    notes: Optional[str] = Field(None, max_length=500)


class StoryGenerateRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    mode: Literal["full", "continue", "rewrite", "quiz"]
    run_async: bool = Field(False, alias="async")
    contextId: str
    narratorId: Optional[str] = None
    characterIds: List[str] = Field(default_factory=list)
    options: StoryOptions = Field(default_factory=StoryOptions)
    backgrounds: List[Background] = Field(min_length=1)
    previousSteps: List[dict] = Field(default_factory=list)
    step: Optional[dict] = None
    instruction: Optional[str] = Field(None, max_length=300)
    count: Optional[int] = Field(None, ge=3, le=5)
    contextData: Optional[HistoricalContextData] = None
    characterData: List[CharacterData] = Field(default_factory=list)


class FactionInput(BaseModel):
    id: str
    name: str
    color: Optional[str] = None


class AnchorInput(BaseModel):
    id: str
    name: str
    hint: Optional[str] = None


class BattlePlanOptions(BaseModel):
    phaseCount: int = Field(5, ge=3, le=8)
    perspective: str = "neutral"
    gradeLevel: str = "THCS"
    notes: Optional[str] = Field(None, max_length=500)


class BattlePlanRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    run_async: bool = Field(False, alias="async")
    contextId: str
    options: BattlePlanOptions = Field(default_factory=BattlePlanOptions)
    existingFactions: List[FactionInput] = Field(default_factory=list)
    existingAnchors: List[AnchorInput] = Field(default_factory=list)
    storySummary: Optional[str] = Field(None, max_length=2000)
    contextData: Optional[HistoricalContextData] = None
    characterData: List[CharacterData] = Field(default_factory=list)


class LocateAnchorsRequest(BaseModel):
    imageUrl: Optional[str] = None
    imageBase64: Optional[str] = None
    anchors: List[AnchorInput] = Field(min_length=1, max_length=10)
