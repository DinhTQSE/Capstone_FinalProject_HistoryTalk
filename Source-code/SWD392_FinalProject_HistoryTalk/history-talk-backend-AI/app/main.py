"""FastAPI application entry point."""

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.config import settings
from app.routers import chat

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

# Allow all origins in development; restrict in production via environment variable.
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(chat.router)


@app.get("/health", tags=["Health"])
async def health():
    return {"status": "ok", "llm_provider": settings.LLM_PROVIDER, "llm_model": settings.LLM_MODEL}
