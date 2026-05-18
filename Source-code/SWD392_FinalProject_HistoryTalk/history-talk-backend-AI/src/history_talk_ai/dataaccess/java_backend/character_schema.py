"""Pydantic models that mirror the Java backend's API responses."""

from typing import List, Optional
from pydantic import BaseModel


class ContextSummary(BaseModel):
    contextId: str
    name: str


class EventSummary(BaseModel):
    id: Optional[str] = None
    name: Optional[str] = None
    era: Optional[str] = None
    year: Optional[int] = None


class CreatedBy(BaseModel):
    uid: Optional[str] = None
    userName: Optional[str] = None


class CharacterData(BaseModel):
    """Maps to CharacterResponse from the Java backend."""

    characterId: str
    name: str
    title: Optional[str] = None
    background: str
    image: Optional[str] = None
    personality: Optional[str] = None
    lifespan: Optional[str] = None
    side: Optional[str] = None
    era: Optional[str] = None
    context: Optional[ContextSummary] = None
    events: Optional[List[EventSummary]] = None
    createdBy: Optional[CreatedBy] = None
