"""FastAPI application entry point."""

import os
from pathlib import Path

from dotenv import load_dotenv
# Resolve .env relative to the project root (2 levels up from this file)
_ENV_PATH = Path(__file__).resolve().parents[3] / ".env"
load_dotenv(dotenv_path=_ENV_PATH)

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from history_talk_ai.common.config.settings import settings
from history_talk_ai.presentation.chat import router as chat_router

app = FastAPI(
    title="HistoryTalk AI Service",
    description=(
        "LangChain-powered roleplay API. "
        "Receives a user message + conversation history, fetches character and "
        "historical-context data from the Java backend, then invokes the LLM to produce "
        "an in-character response."
    ),
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(chat_router.router)


@app.get("/health", tags=["Health"])
async def health():
    return {"status": "ok", "llm_model": settings.LLM_MODEL}
