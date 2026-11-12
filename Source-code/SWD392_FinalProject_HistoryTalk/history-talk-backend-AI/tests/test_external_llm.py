import asyncio
import json
import os
import sys
from pathlib import Path

import httpx
import pytest

os.environ.setdefault("SUPABASE_URL", "http://localhost:54321")
os.environ.setdefault("SUPABASE_KEY", "test-key")
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from history_talk_ai.application.chat import service  # noqa: E402

MESSAGES = [{"role": "user", "content": "Hỏi"}]


@pytest.fixture(autouse=True)
def external_provider(monkeypatch):
    monkeypatch.setattr(service.settings, "LLM_PROVIDER", "external")
    monkeypatch.setattr(service.settings, "EXTERNAL_API_BASE_URL", "https://bedrock.example/openai/v1/")
    monkeypatch.setattr(service.settings, "EXTERNAL_API_KEY", "test-key")
    monkeypatch.setattr(service.settings, "LLM_MODEL", "openai.gpt-test")
    monkeypatch.setattr(service.settings, "EXTERNAL_REASONING_EFFORT", "low")
    monkeypatch.setattr(service, "_external_dropped_params", set())
    monkeypatch.setattr(service, "_external_max_tokens_key", ["max_completion_tokens"])


def _use_transport(monkeypatch, handler):
    """Routes the shared client through a fake server; returns the JSON bodies it received."""
    requests = []

    def record(request: httpx.Request) -> httpx.Response:
        requests.append(json.loads(request.content))
        return handler(request, len(requests))

    client = httpx.AsyncClient(transport=httpx.MockTransport(record))
    monkeypatch.setattr(service, "ollama_client", client)
    return requests


def _completion(content, prompt_tokens=120, completion_tokens=30):
    return httpx.Response(200, json={
        "choices": [{"message": {"content": content}}],
        "usage": {"prompt_tokens": prompt_tokens, "completion_tokens": completion_tokens},
    })


def _sse(*events):
    body = "".join(f"data: {json.dumps(e)}\n\n" for e in events) + "data: [DONE]\n\n"
    return httpx.Response(200, text=body, headers={"Content-Type": "text/event-stream"})


def test_prompt_tokens_are_divided_by_ten_like_the_self_hosted_model(monkeypatch):
    requests = _use_transport(monkeypatch, lambda request, n: _completion("Ta là Ngô Quyền."))

    content, prompt_tokens, completion_tokens = asyncio.run(service._call_llm(MESSAGES, expect_json=False))

    assert (content, prompt_tokens, completion_tokens) == ("Ta là Ngô Quyền.", 12, 30)
    assert requests[0]["model"] == "openai.gpt-test"
    assert requests[0]["max_completion_tokens"] == service.settings.LLM_MAX_TOKENS
    assert requests[0]["reasoning_effort"] == "low"
    assert "response_format" not in requests[0]


def test_json_calls_ask_for_a_json_object(monkeypatch):
    requests = _use_transport(monkeypatch, lambda request, n: _completion('{"ok": true}'))

    asyncio.run(service._call_llm(MESSAGES, expect_json=True, temperature=0.1))

    assert requests[0]["response_format"] == {"type": "json_object"}
    assert requests[0]["temperature"] == 0.1


def test_stream_asks_for_usage_and_divides_prompt_tokens_by_ten(monkeypatch):
    requests = _use_transport(monkeypatch, lambda request, n: _sse(
        {"choices": [{"delta": {"role": "assistant"}}]},
        {"choices": [{"delta": {"content": "Ta là "}}]},
        {"choices": [{"delta": {"content": "Ngô Quyền."}}]},
        {"choices": [], "usage": {"prompt_tokens": 95, "completion_tokens": 8}},
    ))

    async def collect():
        return [chunk async for chunk in service._call_llm_stream(MESSAGES)]

    chunks = asyncio.run(collect())

    assert chunks == ["Ta là ", "Ngô Quyền.", {"prompt_tokens": 9, "completion_tokens": 8}]
    assert requests[0]["stream_options"] == {"include_usage": True}


def test_rejected_parameter_is_dropped_and_stays_dropped(monkeypatch):
    def handler(request, n):
        if "temperature" in json.loads(request.content):
            return httpx.Response(400, json={"error": {"message": "Unsupported parameter: 'temperature' is not supported with this model."}})
        return _completion("Đúng.")

    requests = _use_transport(monkeypatch, handler)

    assert asyncio.run(service._call_llm(MESSAGES, expect_json=False))[0] == "Đúng."
    assert asyncio.run(service._call_llm(MESSAGES, expect_json=False))[0] == "Đúng."

    assert len(requests) == 3
    assert "temperature" not in requests[2]


def test_rejected_max_completion_tokens_falls_back_to_max_tokens(monkeypatch):
    def handler(request, n):
        if "max_completion_tokens" in json.loads(request.content):
            return httpx.Response(400, json={"error": {"message": "Unrecognized request argument: max_completion_tokens"}})
        return _sse({"choices": [{"delta": {"content": "Đúng."}}]})

    requests = _use_transport(monkeypatch, handler)

    async def collect():
        return [chunk async for chunk in service._call_llm_stream(MESSAGES)]

    assert asyncio.run(collect())[0] == "Đúng."
    assert requests[1]["max_tokens"] == service.settings.LLM_MAX_TOKENS


def test_other_errors_are_raised_without_retrying(monkeypatch):
    requests = _use_transport(monkeypatch, lambda request, n: httpx.Response(401, json={"message": "bad key"}))

    with pytest.raises(httpx.HTTPStatusError):
        asyncio.run(service._call_llm(MESSAGES, expect_json=False))

    assert len(requests) == 1


def test_chinese_reply_is_retried_like_the_self_hosted_model(monkeypatch):
    replies = iter(["Ta là 吴权.", "Ta là Ngô Quyền."])
    requests = _use_transport(monkeypatch, lambda request, n: _completion(next(replies), 100, 10))

    assert asyncio.run(service._call_llm(MESSAGES, expect_json=False)) == ("Ta là Ngô Quyền.", 20, 20)
    assert len(requests) == 2
