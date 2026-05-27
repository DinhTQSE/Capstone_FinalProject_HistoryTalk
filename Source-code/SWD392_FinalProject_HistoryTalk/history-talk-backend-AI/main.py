"""Convenience entry-point: python main.py"""

import os
from pathlib import Path
import sys

# Load .env FIRST before any other imports that read environment variables
from dotenv import load_dotenv
load_dotenv(dotenv_path=Path(__file__).resolve().parent / ".env")

import uvicorn

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "src"))

from history_talk_ai.common.config.settings import settings  # noqa: E402

if __name__ == "__main__":
    # In Production (behind Nginx): APP_HOST should be "127.0.0.1" in .env
    # In Local / Dev: APP_HOST="0.0.0.0" to bind all interfaces
    host = os.getenv("APP_HOST", settings.APP_HOST)
    port = int(os.getenv("APP_PORT", settings.APP_PORT))

    uvicorn.run(
        "history_talk_ai.main:app",
        host=host,
        port=port,
        reload=settings.DEBUG,
    )
