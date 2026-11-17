import asyncio
import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

os.environ.setdefault("SUPABASE_URL", "http://localhost:54321")
os.environ.setdefault("SUPABASE_KEY", "test-key")
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from history_talk_ai.application.chat import service  # noqa: E402
from history_talk_ai.presentation.chat import router  # noqa: E402
from history_talk_ai.presentation.chat.schemas import (  # noqa: E402
    DescribeImagesRequest,
    ImageToDescribe,
)


class FakeEmbedResponse:
    def __init__(self, status_code: int, data: dict):
        self.status_code = status_code
        self._data = data

    def raise_for_status(self):
        if self.status_code >= 400:
            raise RuntimeError(f"HTTP {self.status_code}")

    def json(self):
        return self._data


class PdfAiOptimizationsTest(unittest.TestCase):
    def test_describe_images_runs_concurrently_preserves_order_and_handles_item_failure(self):
        active = 0
        max_active = 0

        async def fake_describe(image_base64: str, caption: str | None = None):
            nonlocal active, max_active
            active += 1
            max_active = max(max_active, active)
            try:
                if image_base64 == "img-1":
                    await asyncio.sleep(0.05)
                    return "[desc-1]"
                if image_base64 == "img-2":
                    await asyncio.sleep(0.01)
                    raise RuntimeError("transient vlm timeout")
                await asyncio.sleep(0.01)
                return "[desc-3]"
            finally:
                active -= 1

        req = DescribeImagesRequest(
            images=[
                ImageToDescribe(image_base64="img-1", caption="Cap 1", page_number=1),
                ImageToDescribe(image_base64="img-2", caption="Cap 2", page_number=2),
                ImageToDescribe(image_base64="img-3", caption=None, page_number=3),
            ]
        )

        with patch.object(router.settings, "VISION_CONCURRENCY", 3), patch.object(
            router.llm_service, "describe_historical_image", fake_describe
        ):
            resp = asyncio.run(router.describe_images(req))

        self.assertTrue(resp.success)
        self.assertGreaterEqual(max_active, 2)
        self.assertEqual([item.page_number for item in resp.data], [1, 2, 3])
        self.assertEqual(resp.data[0].description_block, "[desc-1]")
        self.assertIn("Hình ảnh trang 2: Cap 2", resp.data[1].description_block)
        self.assertEqual(resp.data[2].description_block, "[desc-3]")

    def test_get_embeddings_batch_uses_api_embed_and_preserves_blank_slots(self):
        calls = []

        async def fake_post(url, json, auth=None):
            calls.append((url, json))
            self.assertTrue(url.endswith("/api/embed"))
            inputs = json["input"]
            return FakeEmbedResponse(
                200,
                {"embeddings": [[float(len(t)), 1.0] for t in inputs]},
            )

        texts = ["doan 1", "   ", "doan ba", "doan bon", "doan nam"]
        with patch.object(service.ollama_client, "post", fake_post):
            vectors = asyncio.run(service.get_embeddings_batch(texts, batch_size=2))

        self.assertEqual(len(calls), 2)
        self.assertEqual(vectors[0], [6.0, 1.0])
        self.assertEqual(vectors[1], [])
        self.assertEqual(vectors[2], [7.0, 1.0])
        self.assertEqual(vectors[3], [8.0, 1.0])
        self.assertEqual(vectors[4], [8.0, 1.0])

    def test_get_embeddings_batch_falls_back_on_404(self):
        fallback_calls = []

        async def fake_post(url, json, auth=None):
            return FakeEmbedResponse(404, {})

        async def fake_single(text: str, max_retries: int = 3):
            fallback_calls.append(text)
            return [0.5, 0.5]

        with patch.object(service.ollama_client, "post", fake_post), patch.object(
            service, "get_embedding_from_ollama", fake_single
        ):
            vectors = asyncio.run(service.get_embeddings_batch(["a", "b"], batch_size=2))

        self.assertEqual(fallback_calls, ["a", "b"])
        self.assertEqual(vectors, [[0.5, 0.5], [0.5, 0.5]])


if __name__ == "__main__":
    unittest.main()
