"""Story scripts: a flat list of steps linked by "next". The editor builds the graph and the layout."""

from typing import Annotated, Literal

from pydantic import BaseModel, Field

from history_talk_ai.application.authoring.jobs import Progress
from history_talk_ai.application.authoring.sources import format_sources, keep_known_sources, retrieve_sources
from history_talk_ai.application.authoring.structured import generate_structured
from history_talk_ai.application.prompting.authoring_prompts import build_story_prompts

Expression = Literal["neutral", "happy", "serious", "sad", "angry", "surprised"]

STORY_TEMPERATURE = 0.5
_MAX_TOKENS = {"full": 8000, "continue": 2000, "rewrite": 800, "quiz": 1200}


class ChoiceOption(BaseModel):
    label: str = Field(min_length=1, max_length=60)
    next: str


class LineStep(BaseModel):
    id: str
    type: Literal["line"]
    speaker: str
    text: str = Field(min_length=1, max_length=220)
    expression: Expression = "neutral"
    background: str | None = None
    sources: list[str] = []
    next: str | None = None


class ChoiceStep(BaseModel):
    id: str
    type: Literal["choice"]
    speaker: str
    prompt: str = Field(max_length=200)
    options: list[ChoiceOption] = Field(min_length=2, max_length=3)


class QuizStep(BaseModel):
    id: str
    type: Literal["quiz"]
    speaker: str
    question: str = Field(min_length=1, max_length=250)
    answers: list[str] = Field(min_length=2, max_length=4)
    correctIndex: int
    explanation: str = Field(max_length=300)
    sources: list[str] = []
    next: str | None = None
    wrongNext: str | None = None


class EndStep(BaseModel):
    id: str
    type: Literal["end"]
    title: str = Field(max_length=80)
    summary: str = Field(max_length=400)


Step = Annotated[LineStep | ChoiceStep | QuizStep | EndStep, Field(discriminator="type")]


class StoryDraft(BaseModel):
    title: str = Field(default="", max_length=100)
    startId: str
    steps: list[Step] = Field(min_length=1, max_length=40)


def _targets(step) -> list[tuple[str, str | None]]:
    """(field, target id) of every link leaving a step."""
    if isinstance(step, ChoiceStep):
        return [(f"options[{i}].next", option.next) for i, option in enumerate(step.options)]
    if isinstance(step, QuizStep):
        links = [("next", step.next)]
        if step.wrongNext is not None:
            links.append(("wrongNext", step.wrongNext))
        return links
    if isinstance(step, LineStep):
        return [("next", step.next)]
    return []


def _check_graph(draft: StoryDraft, by_id: dict) -> list[str]:
    """From startId every step is reachable, every path ends at "end", and no path goes back."""
    errors = []
    state: dict[str, str] = {}  # "open" while on the current path, "done" once all its paths are checked

    def visit(step_id: str) -> None:
        state[step_id] = "open"
        for _, target in _targets(by_id[step_id]):
            if target not in by_id:
                continue
            if state.get(target) == "open":
                errors.append(f"Bước {step_id}: quay lại bước {target} tạo thành vòng lặp")
            elif target not in state:
                visit(target)
        state[step_id] = "done"

    visit(draft.startId)
    unreachable = [step.id for step in draft.steps if step.id not in state]
    if unreachable:
        errors.append(f"Không đi tới được các bước: {', '.join(unreachable)}")
    return errors


def validate_story(
    draft: StoryDraft,
    mode: str,
    options: dict,
    speakers: set[str],
    backgrounds: set[str],
    known_sources: set[str],
    original_step: dict | None = None,
) -> tuple[list[str], list[str]]:
    """Business checks matching the editor's validateStory, so a draft opens without errors."""
    errors: list[str] = []
    warnings: list[str] = []

    for step in draft.steps:
        if isinstance(step, (LineStep, QuizStep)):
            step.sources = keep_known_sources(step.sources, known_sources)

    ids = [step.id for step in draft.steps]
    duplicates = sorted({step_id for step_id in ids if ids.count(step_id) > 1})
    if duplicates:
        errors.append(f"Trùng id bước: {', '.join(duplicates)}")
    by_id = {step.id: step for step in draft.steps}
    if draft.startId not in by_id:
        errors.append(f"startId '{draft.startId}' không phải id của bước nào")

    allowed_speakers = {"narrator"} | speakers
    for step in draft.steps:
        if not isinstance(step, EndStep) and step.speaker not in allowed_speakers:
            errors.append(f"Bước {step.id}: speaker '{step.speaker}' không có trong danh sách nhân vật")
        if isinstance(step, LineStep) and step.background is not None and step.background not in backgrounds:
            errors.append(f"Bước {step.id}: background '{step.background}' không có trong danh sách cảnh nền")
        if isinstance(step, QuizStep):
            if not 0 <= step.correctIndex < len(step.answers):
                errors.append(f"Bước {step.id}: correctIndex {step.correctIndex} nằm ngoài danh sách đáp án")
            if len({answer.strip().lower() for answer in step.answers}) < len(step.answers):
                errors.append(f"Bước {step.id}: có đáp án trùng nhau")

        # A rewritten step keeps its links, which point at steps outside this reply.
        if mode == "rewrite":
            continue
        for field, target in _targets(step):
            if target is None:
                if mode == "full":
                    errors.append(f"Bước {step.id}: thiếu {field}")
            elif target not in by_id:
                errors.append(f"Bước {step.id}: {field} trỏ tới '{target}' không tồn tại")

    if mode == "full":
        ends = [step for step in draft.steps if isinstance(step, EndStep)]
        if len(ends) != 1:
            errors.append(f"Kịch bản cần đúng 1 bước end, đang có {len(ends)}")
        if not duplicates and draft.startId in by_id:
            errors.extend(_check_graph(draft, by_id))
        for kind, cls, key in (("choice", ChoiceStep, "choiceCount"), ("quiz", QuizStep, "quizCount")):
            wanted = options.get(key, 1)
            actual = sum(isinstance(step, cls) for step in draft.steps)
            if abs(actual - wanted) == 1:
                warnings.append(f"Cần {wanted} bước {kind}, đang có {actual}")
            elif actual != wanted:
                errors.append(f"Cần đúng {wanted} bước {kind}, đang có {actual}")
    elif mode == "rewrite":
        if len(draft.steps) != 1 or not isinstance(draft.steps[0], LineStep):
            errors.append("Chỉ trả về đúng 1 bước line đã viết lại")
        elif original_step:
            step = draft.steps[0]
            for key in ("id", "speaker", "next"):
                if getattr(step, key) != original_step.get(key):
                    errors.append(f"Phải giữ nguyên {key} của bước cần viết lại")
    elif mode == "quiz":
        if len(draft.steps) != 1 or not isinstance(draft.steps[0], QuizStep):
            errors.append("Chỉ trả về đúng 1 bước quiz")
    elif mode == "continue":
        last = draft.steps[-1]
        if isinstance(last, (LineStep, QuizStep)) and last.next is not None:
            errors.append(f"Bước cuối {last.id} phải có next là null")

    return errors, warnings


def _retrieval_query(context: dict, options: dict, previous_steps: list[dict], step: dict | None) -> str:
    recent = " ".join(s.get("text") or s.get("question") or "" for s in previous_steps[-5:])
    parts = [context.get("name", ""), context.get("description", ""), options.get("notes") or "", recent]
    if step:
        parts.append(step.get("text") or "")
    return " ".join(part for part in parts if part)[:2000]


async def generate_story(
    mode: str,
    context: dict,
    characters: list[dict],
    narrator_id: str | None,
    backgrounds: list[dict],
    options: dict,
    progress: Progress,
    previous_steps: list[dict] | None = None,
    step: dict | None = None,
    instruction: str | None = None,
    count: int | None = None,
) -> dict:
    progress.update("retrieving", 10)
    previous_steps = previous_steps or []
    entity_ids = [context["contextId"]] + [c["characterId"] for c in characters]
    chunks = await retrieve_sources(_retrieval_query(context, options, previous_steps, step), entity_ids)

    narrator = next((c for c in characters if c["characterId"] == narrator_id), None)
    system, user = build_story_prompts(
        mode, context, narrator, characters, backgrounds, options, format_sources(chunks),
        previous_steps=previous_steps, step=step, instruction=instruction, count=count,
    )

    speakers = {c["characterId"] for c in characters}
    background_ids = {b["id"] for b in backgrounds}
    known = {chunk.id for chunk in chunks}

    def check(draft: StoryDraft):
        return validate_story(draft, mode, options, speakers, background_ids, known, step)

    result = await generate_structured(
        system, user, StoryDraft, check, progress, temperature=STORY_TEMPERATURE, max_tokens=_MAX_TOKENS[mode]
    )
    return {
        **result.data,
        "warnings": result.warnings,
        "tokenUsage": {
            "promptTokens": result.prompt_tokens,
            "completionTokens": result.completion_tokens,
            "totalTokens": result.prompt_tokens + result.completion_tokens,
        },
    }
