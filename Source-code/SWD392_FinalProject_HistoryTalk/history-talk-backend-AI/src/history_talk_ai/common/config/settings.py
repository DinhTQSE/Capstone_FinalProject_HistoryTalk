from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # ── Java backend ──────────────────────────────────────────────────────────
    JAVA_BACKEND_URL: str = "http://localhost:8080/Historical-tell"
    CHARACTER_API_PATH: str = "/api/v1/characters"
    CONTEXT_API_PATH: str = "/api/v1/historical-contexts"
    # Request timeout in seconds when calling the Java backend
    JAVA_CLIENT_TIMEOUT: float = 10.0

    # ── LLM (Ollama / External) ───────────────────────────────────────────────
    LLM_PROVIDER: str = "ollama"  # "ollama", "external" (OpenAI, Gemini, Groq, v.v.)
    EXTERNAL_API_BASE_URL: str = "https://generativelanguage.googleapis.com/v1beta/openai/"
    EXTERNAL_API_KEY: str = ""
    
    OLLAMA_BASE_URL: str = "http://localhost:11434"
    OLLAMA_USERNAME: str = ""
    OLLAMA_PASSWORD: str = ""
    LLM_MODEL: str = "qwen3.8:27b"
    LLM_TEMPERATURE: float = 0.7
    # Max tokens for character response (not counting the structured wrapper)
    LLM_MAX_TOKENS: int = 1024
    # Reranked chunks put into the prompt. Each one adds noticeably to the time to first token.
    RAG_TOP_K: int = 3
    # Used for questions such as "tóm tắt cả trận đánh". The reranker on the LLM server returns at most 5.
    RAG_TOP_K_BROAD: int = 5

    # ── Supabase ──────────────────────────────────────────────────────────────
    SUPABASE_URL: str
    SUPABASE_KEY: str
    SUPABASE_SCHEMA: str = "historical_schema"

    # ── App ───────────────────────────────────────────────────────────────────
    APP_HOST: str = "0.0.0.0"
    APP_PORT: int = 8001
    DEBUG: bool = False

    model_config = {"env_file": ".env", "env_file_encoding": "utf-8"}


settings = Settings()
