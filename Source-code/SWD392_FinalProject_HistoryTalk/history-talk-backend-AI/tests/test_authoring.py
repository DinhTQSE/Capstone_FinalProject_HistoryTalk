import asyncio
import json
import os
import sys
from pathlib import Path
from types import SimpleNamespace

import pytest

os.environ.setdefault("SUPABASE_URL", "http://localhost:54321")
os.environ.setdefault("SUPABASE_KEY", "test-key")
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from fastapi.testclient import TestClient  # noqa: E402

from history_talk_ai.application.authoring import battle_map, jobs, sources, story, structured  # noqa: E402
from history_talk_ai.application.chat import service as llm_service  # noqa: E402
from history_talk_ai.main import app  # noqa: E402

CONTEXT = {"contextId": "ctx-1", "name": "Trận Bạch Đằng", "description": "Năm 938.", "year": 938}
NARRATOR = {"characterId": "char-ngo-quyen", "name": "Ngô Quyền", "background": "Tướng nhà Ngô."}
FULL_OPTIONS = {"choiceCount": 1, "quizCount": 1}


def _story(steps, start="s1"):
    return story.StoryDraft.model_validate({"title": "Bạch Đằng", "startId": start, "steps": steps})


def _line(step_id, next_id, **extra):
    return {"id": step_id, "type": "line", "speaker": "narrator", "text": "Năm 938.", "sources": ["chunk_a"],
            "next": next_id, **extra}


def _full_steps():
    return [
        _line("s1", "s2"),
        {"id": "s2", "type": "choice", "speaker": "char-ngo-quyen", "prompt": "Nghe gì trước?",
         "options": [{"label": "Bãi cọc", "next": "s3"}, {"label": "Nam Hán", "next": "s4"}]},
        _line("s3", "s5"),
        _line("s4", "s5"),
        {"id": "s5", "type": "quiz", "speaker": "char-ngo-quyen", "question": "Ta dựa vào gì?",
         "answers": ["Thuỷ triều", "Gió", "Sương"], "correctIndex": 0, "explanation": "Triều rút.",
         "sources": ["chunk_a", "chunk_bia"], "next": "s7", "wrongNext": "s6"},
        _line("s6", "s7"),
        {"id": "s7", "type": "end", "title": "Chiến thắng", "summary": "Đánh tan quân Nam Hán."},
    ]


def _validate(draft, mode="full", options=FULL_OPTIONS, original=None):
    return story.validate_story(draft, mode, options, {"char-ngo-quyen"}, {"river"}, {"chunk_a"}, original)


# ── Story checks ──────────────────────────────────────────────────────────────


def test_valid_story_passes_and_made_up_sources_are_dropped():
    draft = _story(_full_steps())

    errors, warnings = _validate(draft)

    assert (errors, warnings) == ([], [])
    assert draft.steps[4].sources == ["chunk_a"]


def test_dangling_next_and_unreachable_steps_are_errors():
    steps = _full_steps()
    steps[2]["next"] = "s99"
    steps.append(_line("s8", "s7"))

    errors, _ = _validate(_story(steps))

    assert any("'s99' không tồn tại" in e for e in errors)
    assert any("s8" in e and "Không đi tới" in e for e in errors)


def test_going_back_to_an_earlier_step_is_a_loop():
    steps = _full_steps()
    steps[5]["next"] = "s1"

    errors, _ = _validate(_story(steps))

    assert any("vòng lặp" in e for e in errors)


def test_unknown_speaker_and_background_are_errors():
    steps = _full_steps()
    steps[0] = _line("s1", "s2", speaker="char-tran-hung-dao", background="palace")

    errors, _ = _validate(_story(steps))

    assert any("speaker 'char-tran-hung-dao'" in e for e in errors)
    assert any("background 'palace'" in e for e in errors)


def test_one_quiz_too_many_is_only_a_warning():
    errors, warnings = _validate(_story(_full_steps()), options={"choiceCount": 1, "quizCount": 0})

    assert errors == []
    assert warnings == ["Cần 0 bước quiz, đang có 1"]


def test_continued_steps_end_with_a_null_next():
    draft = _story([_line("n1", "n2"), _line("n2", None)], start="n1")

    assert _validate(draft, mode="continue") == ([], [])


def test_rewrite_must_keep_the_links():
    draft = _story([_line("s3", "s9", text="Ngắn hơn.")], start="s3")

    errors, _ = _validate(draft, mode="rewrite", original={"id": "s3", "speaker": "narrator", "next": "s4"})

    assert errors == ["Phải giữ nguyên next của bước cần viết lại"]


# ── Generation with one repair ────────────────────────────────────────────────


def _stub_llm(monkeypatch, *replies):
    calls = []
    pending = list(replies)

    async def fake_call(messages, expect_json=True, temperature=None, **options):
        calls.append({"messages": messages, "options": options})
        return pending.pop(0), 100, 50

    monkeypatch.setattr(llm_service, "_call_llm", fake_call)
    return calls


def _check(draft):
    return _validate(draft)


def test_broken_draft_is_repaired_once(monkeypatch):
    broken = {"title": "Bạch Đằng", "startId": "s1", "steps": _full_steps()}
    broken["steps"][2]["next"] = "s99"
    fixed = {"title": "Bạch Đằng", "startId": "s1", "steps": _full_steps()}
    calls = _stub_llm(monkeypatch, json.dumps(broken), json.dumps(fixed))

    result = asyncio.run(structured.generate_structured("sys", "user", story.StoryDraft, _check, jobs.Progress()))

    assert result.warnings == []
    assert result.data["steps"][2]["next"] == "s5"
    assert (result.prompt_tokens, result.completion_tokens) == (200, 100)
    assert "'s99' không tồn tại" in calls[1]["messages"][-1]["content"]
    assert calls[0]["options"]["max_tokens"] == 6000
    assert "properties" in calls[0]["options"]["json_schema"]


def test_draft_still_broken_after_repair_comes_back_with_warnings(monkeypatch):
    broken = {"title": "Bạch Đằng", "startId": "s1", "steps": _full_steps()}
    broken["steps"][2]["next"] = "s99"
    _stub_llm(monkeypatch, json.dumps(broken), json.dumps(broken))

    result = asyncio.run(structured.generate_structured("sys", "user", story.StoryDraft, _check, jobs.Progress()))

    assert any("'s99' không tồn tại" in w for w in result.warnings)
    assert result.data["startId"] == "s1"


def test_reply_that_is_never_json_fails(monkeypatch):
    _stub_llm(monkeypatch, "không phải json", "vẫn không phải")

    with pytest.raises(ValueError):
        asyncio.run(structured.generate_structured("sys", "user", story.StoryDraft, _check, jobs.Progress()))


# ── Sources ───────────────────────────────────────────────────────────────────


def _stub_rows(monkeypatch, rows):
    async def fake_embedding(text):
        return [0.1, 0.2]

    rpc = lambda name, params: SimpleNamespace(execute=lambda: SimpleNamespace(data=rows))  # noqa: E731
    monkeypatch.setattr(llm_service, "get_embedding_from_ollama", fake_embedding)
    monkeypatch.setattr(llm_service, "supabase", SimpleNamespace(schema=lambda name: SimpleNamespace(rpc=rpc)))


def test_context_without_documents_is_rejected_instead_of_invented(monkeypatch):
    _stub_rows(monkeypatch, [])

    with pytest.raises(jobs.AuthoringInputError):
        asyncio.run(sources.retrieve_sources("Bạch Đằng", ["ctx-1"]))


def test_chunks_get_short_stable_ids(monkeypatch):
    _stub_rows(monkeypatch, [{"chunk_id": "1a2b3c4d-0000-0000-0000-000000000000", "content": "Cọc gỗ lim."}])

    chunks = asyncio.run(sources.retrieve_sources("Bạch Đằng", ["ctx-1"]))

    assert chunks == [sources.SourceChunk("chunk_1a2b3c4d", "Cọc gỗ lim.")]


# ── Battle-map plan ───────────────────────────────────────────────────────────


def _plan(phases, factions=None, anchors=None):
    return battle_map.BattlePlan.model_validate({
        "factions": factions or [{"id": "ta", "name": "Quân Ngô Quyền", "side": "ally"},
                                 {"id": "dich", "name": "Quân Nam Hán", "side": "enemy"}],
        "anchors": anchors or [{"id": "bien", "name": "Biển"}, {"id": "cua-song", "name": "Cửa sông"},
                               {"id": "thuong-luu", "name": "Thượng lưu"}],
        "phases": phases,
    })


def _phase(*symbols):
    return {"title": "Giai đoạn", "narration": "Diễn biến.", "sources": ["chunk_a", "chunk_x"], "symbols": list(symbols)}


def _validate_plan(plan, existing=None, year=938, phase_count=3):
    return battle_map.validate_plan(plan, {"phaseCount": phase_count}, existing or [], {"chunk_a"}, year)


def test_valid_plan_passes_and_drops_unused_anchors():
    plan = _plan([
        _phase({"type": "stakes", "faction": "ta", "at": "cua-song"}),
        _phase({"type": "arrow", "faction": "dich", "from": "bien", "to": "cua-song"}),
        _phase({"type": "victory", "faction": "ta", "at": "cua-song"}),
    ], anchors=[{"id": "bien", "name": "Biển"}, {"id": "cua-song", "name": "Cửa sông"},
                {"id": "thuong-luu", "name": "Thượng lưu"}, {"id": "nui", "name": "Núi"}])

    errors, warnings = _validate_plan(plan)

    assert errors == []
    assert [a.id for a in plan.anchors] == ["bien", "cua-song"]
    assert "nui" in warnings[0]
    assert plan.phases[0].sources == ["chunk_a"]


def test_movement_needs_two_different_landmarks():
    plan = _plan([
        _phase({"type": "arrow", "faction": "dich", "from": "bien"}),
        _phase({"type": "retreat", "faction": "ta", "from": "bien", "to": "bien"}),
        _phase({"type": "clash", "faction": "ta", "at": "song-lo"}),
    ])

    errors, _ = _validate_plan(plan)

    assert any("arrow cần có cả from và to" in e for e in errors)
    assert any("from trùng to" in e for e in errors)
    assert any("'song-lo' không tồn tại" in e for e in errors)


def test_existing_faction_keeps_its_id():
    plan = _plan([_phase({"type": "camp", "faction": "quan-ngo", "at": "bien"})] * 3,
                 factions=[{"id": "quan-ngo", "name": "Quân Ngô Quyền"}, {"id": "dich", "name": "Quân Nam Hán"}])

    errors, _ = _validate_plan(plan, existing=[{"id": "ta", "name": "Quân Ngô Quyền"}])

    assert "Phe 'Quân Ngô Quyền' đã có, phải giữ id 'ta'" in errors


def test_ancient_battle_cannot_use_tanks_or_planes():
    plan = _plan([_phase({"type": "armor", "faction": "ta", "at": "bien"},
                         {"type": "airforce", "faction": "dich", "at": "cua-song"})] * 3)

    errors, _ = _validate_plan(plan, year=938)

    assert any("chưa có armor" in e for e in errors)
    assert any("chưa có airforce" in e for e in errors)
    assert battle_map.forbidden_types(1954) == set()
    assert battle_map.context_year({"year": 179, "isBC": True}) == -179


# ── Locating landmarks ────────────────────────────────────────────────────────


def test_points_on_the_0_1000_scale_and_in_pixels_become_percent():
    assert battle_map.to_percent([615, 482], 1280, 960) == (61.5, 48.2)
    assert battle_map.to_percent([1100, 480], 1280, 960) == (85.9, 50.0)
    assert battle_map.to_percent([1500, 480], 1280, 960) is None
    assert battle_map.to_percent(None, 1280, 960) is None


def test_locate_uses_the_vision_model_and_reports_unsure_landmarks(monkeypatch):
    import base64
    import io

    from PIL import Image

    buffer = io.BytesIO()
    Image.new("RGB", (2560, 1920), "white").save(buffer, format="PNG")
    captured = {}

    async def fake_post(url, json, auth, timeout):
        captured.update(json)
        content = '{"anchors": [{"id": "cua-song", "found": true, "point": [615, 482], "confidence": 0.7},' \
                  ' {"id": "bien", "found": false}]}'
        return SimpleNamespace(raise_for_status=lambda: None, json=lambda: {"message": {"content": content}})

    monkeypatch.setattr(llm_service.ollama_client, "post", fake_post)

    result = asyncio.run(battle_map.locate_anchors(None, base64.b64encode(buffer.getvalue()).decode(), [
        {"id": "cua-song", "name": "Cửa sông Bạch Đằng"}, {"id": "bien", "name": "Biển"},
    ]))

    assert result["anchors"] == [
        {"id": "cua-song", "found": True, "x": 61.5, "y": 48.2, "confidence": 0.7},
        {"id": "bien", "found": False},
    ]
    assert captured["model"] == llm_service.settings.LOCATE_MODEL
    assert captured["think"] is False


# ── API ───────────────────────────────────────────────────────────────────────


def _story_body(**extra):
    return {"mode": "full", "contextId": "ctx-1", "narratorId": "char-ngo-quyen", "characterIds": ["char-ngo-quyen"],
            "backgrounds": [{"id": "river", "name": "Sông"}],
            "contextData": {**CONTEXT, "contextId": "ctx-1"}, "characterData": [NARRATOR], **extra}


def test_continue_without_previous_steps_is_rejected():
    response = TestClient(app).post("/v1/ai/story/generate", json=_story_body(mode="continue", count=3))

    assert response.status_code == 422
    assert "previousSteps" in response.json()["detail"]


def test_async_draft_runs_as_a_job(monkeypatch):
    async def fake_generate(**kwargs):
        kwargs["progress"].update("generating", 40)
        return {"title": "Bạch Đằng", "startId": "s1", "steps": [], "warnings": []}

    monkeypatch.setattr("history_talk_ai.presentation.authoring.router.generate_story", fake_generate)

    with TestClient(app) as client:
        created = client.post("/v1/ai/story/generate", json=_story_body(**{"async": True}))
        assert created.status_code == 202
        job_id = created.json()["data"]["jobId"]
        status = client.get(f"/v1/ai/jobs/{job_id}").json()["data"]
        for _ in range(20):
            if status["status"] == "succeeded":
                break
            status = client.get(f"/v1/ai/jobs/{job_id}").json()["data"]

    assert status["status"] == "succeeded"
    assert status["result"]["title"] == "Bạch Đằng"
    assert status["stage"] == "done"


def test_context_without_documents_answers_422(monkeypatch):
    _stub_rows(monkeypatch, [])

    response = TestClient(app).post("/v1/ai/story/generate", json=_story_body())

    assert response.status_code == 422
    assert response.json()["detail"] == "Bối cảnh chưa có tài liệu tham khảo."
