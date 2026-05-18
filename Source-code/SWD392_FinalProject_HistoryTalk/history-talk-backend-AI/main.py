"""Convenience entry-point: python main.py"""

from pathlib import Path
import sys

import uvicorn

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT / "src"))

from history_talk_ai.common.config.settings import settings  # noqa: E402

if __name__ == "__main__":
    uvicorn.run(
        "history_talk_ai.main:app",
        host=settings.APP_HOST,
        port=settings.APP_PORT,
        reload=settings.DEBUG,
    )
