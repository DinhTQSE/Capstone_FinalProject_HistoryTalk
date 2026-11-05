import asyncio
import os
import sys
from pathlib import Path
from types import SimpleNamespace

import pytest

os.environ.setdefault("SUPABASE_URL", "http://localhost:54321")
os.environ.setdefault("SUPABASE_KEY", "test-key")
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from history_talk_ai.application.chat import service  # noqa: E402
from history_talk_ai.dataaccess.java_backend.character_schema import CharacterData  # noqa: E402


class FakeResponse:
    def __init__(self, data):
        self.data = data

    def raise_for_status(self):
        pass

    def json(self):
        return self.data


def _stub_provider(monkeypatch, *replies):
    calls = []
    pending = list(replies)

    async def fake_provider(messages, expect_json, temperature):
        calls.append(messages)
        return pending.pop(0), 10, 5

    monkeypatch.setattr(service, "_call_provider", fake_provider)
    return calls


def test_ollama_call_disables_thinking(monkeypatch):
    captured = {}

    async def fake_post(url, json, auth):
        captured.update(json)
        return FakeResponse({"message": {"content": "Xin chào"}})

    monkeypatch.setattr(service.ollama_client, "post", fake_post)

    asyncio.run(service._call_ollama([{"role": "user", "content": "Hỏi"}], expect_json=False))

    assert captured["think"] is False


def test_vietnamese_reply_is_not_retried(monkeypatch):
    calls = _stub_provider(monkeypatch, "Ta là Ngô Quyền.")

    assert asyncio.run(service._call_llm([{"role": "user", "content": "Hỏi"}])) == ("Ta là Ngô Quyền.", 10, 5)
    assert len(calls) == 1


def test_chinese_reply_is_retried_and_tokens_are_counted_twice(monkeypatch):
    calls = _stub_provider(monkeypatch, "Ta là 吴权.", "Ta là Ngô Quyền.")

    assert asyncio.run(service._call_llm([{"role": "user", "content": "Hỏi"}])) == ("Ta là Ngô Quyền.", 20, 10)
    assert len(calls) == 2


def test_chinese_reply_fails_after_one_retry(monkeypatch):
    _stub_provider(monkeypatch, "吴权", "吴权")

    with pytest.raises(ValueError):
        asyncio.run(service._call_llm([{"role": "user", "content": "Hỏi"}]))


def test_han_characters_are_allowed_when_the_source_quotes_them(monkeypatch):
    calls = _stub_provider(monkeypatch, "Bài thơ mở đầu bằng 南國山河.")

    content, _, _ = asyncio.run(service._call_llm([{"role": "user", "content": "Tư liệu: 南國山河南帝居"}]))

    assert "南國山河" in content
    assert len(calls) == 1


def test_rerank_goes_through_the_authenticated_gateway(monkeypatch):
    captured = {}

    async def fake_embedding(text):
        return [0.1]

    async def fake_post(url, json, auth):
        captured.update(url=url, auth=auth)
        return FakeResponse({"top_docs": ["Đoạn 1"]})

    fake_rpc = lambda name, params: SimpleNamespace(
        execute=lambda: SimpleNamespace(data=[{"content": "Đoạn 1"}, {"content": "Đoạn 2"}])
    )
    monkeypatch.setattr(service, "get_embedding_from_ollama", fake_embedding)
    monkeypatch.setattr(service, "supabase", SimpleNamespace(schema=lambda name: SimpleNamespace(rpc=fake_rpc)))
    monkeypatch.setattr(service.ollama_client, "post", fake_post)
    monkeypatch.setattr(service.settings, "OLLAMA_BASE_URL", "https://llm.example:11434/")
    monkeypatch.setattr(service.settings, "OLLAMA_USERNAME", "historytalk")
    monkeypatch.setattr(service.settings, "OLLAMA_PASSWORD", "secret")

    context, top_docs = asyncio.run(service.retrieve_history_context("Hỏi", ["char-1"]))

    assert captured["url"] == "https://llm.example:11434/api/rerank"
    assert captured["auth"] == ("historytalk", "secret")
    assert top_docs == ["Đoạn 1"]
    assert context == "Đoạn 1"


def test_chat_prompt_is_sent_as_a_system_message(monkeypatch):
    captured = {}

    async def fake_retrieve(question, entity_ids):
        return "", []

    async def fake_llm(messages, expect_json=True, temperature=None):
        captured["messages"] = messages
        return "Ta là Ngô Quyền.", 10, 5

    monkeypatch.setattr(service, "retrieve_history_context", fake_retrieve)
    monkeypatch.setattr(service, "_call_llm", fake_llm)
    character = CharacterData(characterId="char-1", name="Ngô Quyền", background="Tiểu sử")

    asyncio.run(service.generate_reply(character, None, "Ngươi là ai?", [], skip_suggestions=True))

    assert captured["messages"][0]["role"] == "system"
    assert "Ngô Quyền" in captured["messages"][0]["content"]
    assert captured["messages"][-1] == {"role": "user", "content": "Ngươi là ai?"}


def test_stream_forwards_chunks_without_artificial_delay(monkeypatch):
    async def fake_retrieve(question, entity_ids):
        return "", []

    async def fake_stream(messages):
        yield "Ta là "
        yield "Ngô Quyền."
        yield {"prompt_tokens": 10, "completion_tokens": 5}

    async def forbidden_sleep(seconds):
        raise AssertionError("stream must not sleep between chunks")

    monkeypatch.setattr(service, "retrieve_history_context", fake_retrieve)
    monkeypatch.setattr(service, "_call_llm_stream", fake_stream)
    monkeypatch.setattr(service.asyncio, "sleep", forbidden_sleep)
    character = CharacterData(characterId="char-1", name="Ngô Quyền", background="Tiểu sử")

    async def collect():
        return [
            chunk
            async for chunk in service.generate_reply_stream(character, None, "Ngươi là ai?", [], skip_suggestions=True)
        ]

    chunks = asyncio.run(collect())

    assert chunks[:2] == ["Ta là ", "Ngô Quyền."]
    assert chunks[2]["promptTokens"] == 10
