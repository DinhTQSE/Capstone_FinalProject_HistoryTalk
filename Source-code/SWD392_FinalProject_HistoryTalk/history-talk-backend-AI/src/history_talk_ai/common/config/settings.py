from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # ── Java backend ──────────────────────────────────────────────────────────
    JAVA_BACKEND_URL: str = "http://localhost:8080/Historical-tell"
    CHARACTER_API_PATH: str = "/api/v1/characters"
    CONTEXT_API_PATH: str = "/api/v1/historical-contexts"
    # Request timeout in seconds when calling the Java backend
    JAVA_CLIENT_TIMEOUT: float = 10.0

    # ── LLM (Ollama) ──────────────────────────────────────────────────────────
    OLLAMA_BASE_URL: str
    OLLAMA_USERNAME: str = ""
    OLLAMA_PASSWORD: str = ""
    LLM_MODEL: str = "qwen2.5:14b"
    LLM_TEMPERATURE: float = 0.7
    # Max tokens for character response (not counting the structured wrapper)
    LLM_MAX_TOKENS: int = 1024

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
