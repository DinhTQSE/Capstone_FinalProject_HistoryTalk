"""Async HTTP client that calls the Java Spring Boot backend."""

import httpx

from app.config import settings
from app.models.character import CharacterData
from app.models.historical_context import HistoricalContextData


class JavaBackendError(Exception):
    def __init__(self, status_code: int, message: str):
        self.status_code = status_code
        self.message = message
        super().__init__(f"[{status_code}] {message}")


class JavaClientNotFoundError(JavaBackendError):
    pass


async def _get(path: str) -> dict:
    """Shared GET helper with timeout and error mapping."""
    url = f"{settings.JAVA_BACKEND_URL}{path}"
    async with httpx.AsyncClient(timeout=settings.JAVA_CLIENT_TIMEOUT) as client:
        try:
            response = await client.get(url)
        except httpx.TimeoutException:
            raise JavaBackendError(504, f"Timeout calling Java backend at {url}")
        except httpx.RequestError as exc:
            raise JavaBackendError(503, f"Cannot reach Java backend: {exc}")

    if response.status_code == 404:
        raise JavaClientNotFoundError(404, f"Resource not found: {path}")
    if not response.is_success:
        raise JavaBackendError(response.status_code, f"Java backend returned {response.status_code}")

    body = response.json()
    # Unwrap ApiResponse wrapper: { success, message, data }
    if isinstance(body, dict) and "data" in body:
        return body["data"]
    return body


async def get_character(character_id: str) -> CharacterData:
    """Fetch a character by UUID from the Java backend."""
    data = await _get(f"{settings.CHARACTER_API_PATH}/{character_id}")
    return CharacterData.model_validate(data)


async def get_historical_context(context_id: str) -> HistoricalContextData:
    """Fetch a historical context by UUID from the Java backend."""
    data = await _get(f"{settings.CONTEXT_API_PATH}/{context_id}")
    return HistoricalContextData.model_validate(data)
