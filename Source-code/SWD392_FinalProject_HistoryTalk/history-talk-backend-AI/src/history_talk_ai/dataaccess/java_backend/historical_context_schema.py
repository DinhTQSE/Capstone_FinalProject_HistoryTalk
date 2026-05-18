"""Pydantic models that mirror the Java backend's HistoricalContextResponse."""

from typing import Optional
from pydantic import BaseModel


class CreatedBy(BaseModel):
    uid: Optional[str] = None
    userName: Optional[str] = None


class HistoricalContextData(BaseModel):
    """Maps to HistoricalContextResponse from the Java backend."""

    contextId: str
    name: str
    description: str
    era: Optional[str] = None
    category: Optional[str] = None
    year: Optional[int] = None
    startYear: Optional[int] = None
    endYear: Optional[int] = None
    period: Optional[str] = None
    yearLabel: Optional[str] = None
    beforeTCN: Optional[bool] = None
    location: Optional[str] = None
    imageUrl: Optional[str] = None
    videoUrl: Optional[str] = None
    createdBy: Optional[CreatedBy] = None
    createdDate: Optional[str] = None
    updatedDate: Optional[str] = None
