"""
Asks the LLM for JSON of a given shape, checks it, and asks once more to fix what is wrong.

A draft that still has problems is returned with warnings instead of failing: the author fixes the rest in the
editor. Only a reply that is not JSON at all fails.
"""

import json
from dataclasses import dataclass, field
from typing import Callable, Type

from pydantic import BaseModel, ValidationError

from history_talk_ai.application.authoring.jobs import Progress
from history_talk_ai.application.chat import service as llm_service
from history_talk_ai.application.chat.exam_service import _parse_llm_json
from history_talk_ai.application.prompting.authoring_prompts import build_repair_prompt

# Returns (errors, warnings). Errors are sent back to the model to fix; warnings only reach the author.
Check = Callable[[BaseModel], tuple[list[str], list[str]]]


@dataclass
class StructuredResult:
    data: dict
    warnings: list[str] = field(default_factory=list)
    prompt_tokens: int = 0
    completion_tokens: int = 0


@dataclass
class _Attempt:
    raw: str
    parsed: dict | None = None
    model: BaseModel | None = None
    errors: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)


def _describe_validation_error(error: ValidationError) -> list[str]:
    messages = []
    for item in error.errors():
        location = ".".join(str(part) for part in item["loc"]) or "JSON"
        messages.append(f"{location}: {item['msg']}")
    return messages


def _evaluate(raw: str, model_cls: Type[BaseModel], check: Check) -> _Attempt:
    attempt = _Attempt(raw)
    try:
        attempt.parsed = _parse_llm_json(raw)
    except (ValueError, json.JSONDecodeError) as exc:
        attempt.errors = [f"Không đọc được JSON: {exc}"]
        return attempt
    try:
        attempt.model = model_cls.model_validate(attempt.parsed)
    except ValidationError as exc:
        attempt.errors = _describe_validation_error(exc)
        return attempt
    attempt.errors, attempt.warnings = check(attempt.model)
    return attempt


def _result_of(attempt: _Attempt) -> dict:
    if attempt.model is not None:
        return attempt.model.model_dump(by_alias=True)
    return attempt.parsed


async def generate_structured(
    system_prompt: str,
    user_prompt: str,
    model_cls: Type[BaseModel],
    check: Check,
    progress: Progress,
    temperature: float = 0.5,
    max_tokens: int = 6000,
) -> StructuredResult:
    messages = [{"role": "system", "content": system_prompt}, {"role": "user", "content": user_prompt}]
    options = {"json_schema": model_cls.model_json_schema(), "max_tokens": max_tokens}

    progress.update("generating", 30)
    raw, prompt_tokens, completion_tokens = await llm_service._call_llm(messages, True, temperature, **options)

    progress.update("validating", 70)
    first = _evaluate(raw, model_cls, check)
    best = first
    if first.errors:
        progress.update("repairing", 80)
        repair_messages = messages + [
            {"role": "assistant", "content": raw},
            {"role": "user", "content": build_repair_prompt(first.errors, raw)},
        ]
        repaired_raw, pt, ct = await llm_service._call_llm(repair_messages, True, temperature, **options)
        prompt_tokens += pt
        completion_tokens += ct
        second = _evaluate(repaired_raw, model_cls, check)
        # Prefer the repaired draft unless it got worse, e.g. no longer JSON at all.
        if second.model is not None or (first.model is None and second.parsed is not None):
            best = second

    if best.model is None and best.parsed is None:
        raise ValueError(f"LLM did not return JSON: {best.errors}")

    return StructuredResult(
        data=_result_of(best),
        warnings=best.warnings + best.errors,
        prompt_tokens=prompt_tokens,
        completion_tokens=completion_tokens,
    )
