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


def test_only_the_best_reranked_chunks_reach_the_prompt(monkeypatch):
    async def fake_embedding(text):
        return [0.1]

    async def fake_post(url, json, auth):
        return FakeResponse({"top_docs": ["Đoạn 1", "Đoạn 2", "Đoạn 3", "Đoạn 4", "Đoạn 5"]})

    fake_rpc = lambda name, params: SimpleNamespace(
        execute=lambda: SimpleNamespace(data=[{"content": f"Đoạn {i}"} for i in range(1, 11)])
    )
    monkeypatch.setattr(service, "get_embedding_from_ollama", fake_embedding)
    monkeypatch.setattr(service, "supabase", SimpleNamespace(schema=lambda name: SimpleNamespace(rpc=fake_rpc)))
    monkeypatch.setattr(service.ollama_client, "post", fake_post)
    monkeypatch.setattr(service.settings, "RAG_TOP_K", 3)

    context, top_docs = asyncio.run(service.retrieve_history_context("Hỏi", ["char-1"]))

    assert top_docs == ["Đoạn 1", "Đoạn 2", "Đoạn 3"]
    assert "Đoạn 4" not in context


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
    assert captured["messages"][-1]["role"] == "user"
    assert captured["messages"][-1]["content"].endswith("CÂU HỎI CỦA NGƯỜI DÙNG: Ngươi là ai?")


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


def _character():
    return CharacterData(characterId="char-1", name="Ngô Quyền", background="Tiểu sử")


def test_answer_without_documents_is_still_told_not_to_invent(monkeypatch):
    captured = {}

    async def fake_retrieve(question, entity_ids):
        return "", []

    async def fake_llm(messages, expect_json=True, temperature=None):
        captured["last"] = messages[-1]["content"]
        return "Sử sách không ghi chép rõ.", 10, 5

    monkeypatch.setattr(service, "retrieve_history_context", fake_retrieve)
    monkeypatch.setattr(service, "_call_llm", fake_llm)

    asyncio.run(service.generate_reply(_character(), None, "Năm nào?", [], skip_suggestions=True))

    assert "Sử sách không ghi chép rõ" in captured["last"]


def test_failed_embedding_is_reported_instead_of_treated_as_no_documents(monkeypatch):
    async def fake_embedding(text):
        return []

    monkeypatch.setattr(service, "get_embedding_from_ollama", fake_embedding)

    with pytest.raises(service.RagUnavailableError):
        asyncio.run(service.retrieve_history_context("Hỏi", ["char-1"]))


def test_failed_search_is_reported_instead_of_treated_as_no_documents(monkeypatch):
    async def fake_embedding(text):
        return [0.1]

    def failing_rpc(name, params):
        raise RuntimeError("function does not exist")

    monkeypatch.setattr(service, "get_embedding_from_ollama", fake_embedding)
    monkeypatch.setattr(service, "supabase", SimpleNamespace(schema=lambda name: SimpleNamespace(rpc=failing_rpc)))

    with pytest.raises(service.RagUnavailableError):
        asyncio.run(service.retrieve_history_context("Hỏi", ["char-1"]))


def test_unreachable_llm_server_is_not_retried(monkeypatch):
    calls = []

    async def fake_post(url, json, auth):
        calls.append(url)
        raise service.httpx.ConnectTimeout("server is off")

    monkeypatch.setattr(service.ollama_client, "post", fake_post)

    assert asyncio.run(service.get_embedding_from_ollama("Hỏi")) == []
    assert len(calls) == 1


def test_reply_survives_when_suggestions_fail(monkeypatch):
    async def fake_retrieve(question, entity_ids):
        return "", []

    async def fake_llm(messages, expect_json=True, temperature=None):
        if "gợi ý 3 câu hỏi" in messages[-1]["content"]:
            raise ValueError("LLM reply still contains Chinese characters")
        return "Ta là Ngô Quyền.", 10, 5

    monkeypatch.setattr(service, "retrieve_history_context", fake_retrieve)
    monkeypatch.setattr(service, "_call_llm", fake_llm)

    reply, suggestions, prompt_tokens, completion_tokens, _ = asyncio.run(
        service.generate_reply(_character(), None, "Ngươi là ai?", [])
    )

    assert reply == "Ta là Ngô Quyền."
    assert suggestions == []
    assert (prompt_tokens, completion_tokens) == (10, 5)


def test_suggestions_are_cleaned_of_numbering(monkeypatch):
    async def fake_llm(messages, expect_json=True, temperature=None):
        return '1. Ngài sinh năm nào?\n- "Ngài có con không?"\nok', 10, 5

    monkeypatch.setattr(service, "_call_llm", fake_llm)

    questions, _, _ = asyncio.run(service._generate_suggested_questions([], "Trả lời"))

    assert questions == ["Ngài sinh năm nào?", "Ngài có con không?"]


def test_stream_stops_with_an_error_when_documents_cannot_be_searched(monkeypatch):
    async def fake_retrieve(question, entity_ids):
        raise service.RagUnavailableError("Không tra cứu được tư liệu")

    monkeypatch.setattr(service, "retrieve_history_context", fake_retrieve)

    async def collect():
        return [chunk async for chunk in service.generate_reply_stream(_character(), None, "Hỏi", [])]

    with pytest.raises(service.RagUnavailableError):
        asyncio.run(collect())


def _stub_rerank(monkeypatch, reranked):
    async def fake_embedding(text):
        return [0.1]

    async def fake_post(url, json, auth):
        return FakeResponse({"top_docs": reranked})

    fake_rpc = lambda name, params: SimpleNamespace(
        execute=lambda: SimpleNamespace(data=[{"content": f"Đoạn {i}"} for i in range(1, 11)])
    )
    monkeypatch.setattr(service, "get_embedding_from_ollama", fake_embedding)
    monkeypatch.setattr(service, "supabase", SimpleNamespace(schema=lambda name: SimpleNamespace(rpc=fake_rpc)))
    monkeypatch.setattr(service.ollama_client, "post", fake_post)
    monkeypatch.setattr(service.settings, "RAG_TOP_K", 3)
    monkeypatch.setattr(service.settings, "RAG_TOP_K_BROAD", 5)


@pytest.mark.parametrize(
    "question",
    ["Tóm tắt cả trận đánh cho tôi", "tom tat tran bach dang", "Kể lại diễn biến trận này", "Trình bày toàn bộ cuộc kháng chiến"],
)
def test_broad_questions_are_recognized(question):
    assert service._is_broad_question(question)


@pytest.mark.parametrize("question", ["Tướng địch là ai?", "Trận này diễn ra năm nào?", "Ngài dùng kế gì?"])
def test_fact_questions_are_not_broad(question):
    assert not service._is_broad_question(question)


def test_broad_question_gets_more_chunks(monkeypatch):
    _stub_rerank(monkeypatch, ["Đoạn 1", "Đoạn 2", "Đoạn 3", "Đoạn 4", "Đoạn 5"])

    _, top_docs = asyncio.run(service.retrieve_history_context("Tóm tắt cả trận đánh", ["char-1"]))

    assert len(top_docs) == 5


def test_fact_question_keeps_fewer_chunks(monkeypatch):
    _stub_rerank(monkeypatch, ["Đoạn 1", "Đoạn 2", "Đoạn 3", "Đoạn 4", "Đoạn 5"])

    _, top_docs = asyncio.run(service.retrieve_history_context("Tướng địch là ai?", ["char-1"]))

    assert len(top_docs) == 3


def test_broad_question_falls_back_to_vector_search_when_reranker_keeps_nothing(monkeypatch):
    _stub_rerank(monkeypatch, [])

    _, top_docs = asyncio.run(service.retrieve_history_context("Tóm tắt cả trận đánh", ["char-1"]))

    assert top_docs == ["Đoạn 1", "Đoạn 2", "Đoạn 3", "Đoạn 4", "Đoạn 5"]


def test_fact_question_stays_empty_when_reranker_keeps_nothing(monkeypatch):
    _stub_rerank(monkeypatch, [])

    _, top_docs = asyncio.run(service.retrieve_history_context("Tướng địch là ai?", ["char-1"]))

    assert top_docs == []


def test_broad_question_may_answer_longer():
    assert "200 từ" in service._build_final_user_content("Tóm tắt cả trận đánh", "Tư liệu")
    assert "200 từ" not in service._build_final_user_content("Tướng địch là ai?", "Tư liệu")
