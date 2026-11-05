"""Oral-exam mode: the character asks questions and grades the learner's answers."""

import json
import logging
import random
from typing import List

from history_talk_ai.application.chat import service as llm_service
from history_talk_ai.application.prompting.prompt_builder import (
    build_exam_grading_prompt,
    build_exam_question_prompt,
)
from history_talk_ai.dataaccess.java_backend.character_schema import CharacterData
from history_talk_ai.dataaccess.java_backend.historical_context_schema import HistoricalContextData

logger = logging.getLogger(__name__)

_QUESTION_TEMPERATURE = 0.5
_GRADING_TEMPERATURE = 0.1
_MAX_JSON_ATTEMPTS = 2
_SOURCE_CANDIDATES = 10
_MIN_CHUNK_CHARS = 80
_MAX_FALLBACK_SOURCE_CHARS = 4000
_VERDICTS = {"CORRECT", "PARTIAL", "INCORRECT"}


class ExamSourceUnavailableError(Exception):
    """The character has no documents or background to ask questions from."""


def _parse_llm_json(raw: str) -> dict:
    """Parse a JSON object from LLM output, tolerating code fences and surrounding text."""
    start = raw.find("{")
    end = raw.rfind("}")
    if start == -1 or end <= start:
        raise ValueError(f"No JSON object in LLM output: {raw[:200]}")
    parsed = json.loads(raw[start:end + 1])
    if not isinstance(parsed, dict):
        raise ValueError(f"LLM output is not a JSON object: {raw[:200]}")
    return parsed


def _clean_text(parsed: dict, key: str) -> str:
    value = parsed.get(key)
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"LLM output is missing '{key}'")
    return value.strip()


def _exam_entity_ids(character: CharacterData, context: HistoricalContextData | None) -> List[str]:
    """A session tied to a context is examined on that context only, otherwise on the whole character."""
    if context:
        return [character.characterId, context.contextId]

    entity_ids = [character.characterId]
    for ctx in character.contexts or []:
        if ctx.contextId not in entity_ids:
            entity_ids.append(ctx.contextId)
    for evt in character.events or []:
        if evt.id and evt.id not in entity_ids:
            entity_ids.append(evt.id)
    return entity_ids


async def _pick_source(
    character: CharacterData,
    context: HistoricalContextData | None,
    used_sources: List[str],
) -> str:
    """Pick an unused document chunk, falling back to the character's background text."""
    query = f"{character.name} {context.name}" if context else character.name
    chunks: List[str] = []

    query_vector = await llm_service.get_embedding_from_ollama(query)
    if query_vector:
        try:
            response = llm_service.supabase.schema(llm_service.settings.SUPABASE_SCHEMA).rpc(
                "match_history_chunks",
                {
                    "query_embedding": query_vector,
                    "match_limit": _SOURCE_CANDIDATES,
                    "filter_entity_ids": _exam_entity_ids(character, context),
                },
            ).execute()
            chunks = [
                chunk["content"]
                for chunk in response.data or []
                if len((chunk.get("content") or "").strip()) >= _MIN_CHUNK_CHARS
            ]
        except Exception as e:
            logger.error(f"Supabase RPC error while picking exam source: {e}")

    unused = [chunk for chunk in chunks if chunk not in used_sources]
    if unused or chunks:
        return random.choice(unused or chunks)

    fallback_parts = [character.background]
    if context and context.description:
        fallback_parts.append(context.description)
    fallback = "\n\n".join(part.strip() for part in fallback_parts if part and part.strip())
    if not fallback:
        raise ExamSourceUnavailableError(f"Nhân vật {character.name} chưa có tư liệu để vấn đáp.")
    return fallback[:_MAX_FALLBACK_SOURCE_CHARS]


async def _call_llm_for_json(prompt: str, temperature: float) -> tuple[dict, int, int]:
    """Call the LLM expecting JSON, retrying once on malformed output. Token counts include retries."""
    prompt_tokens = 0
    completion_tokens = 0
    last_error: Exception | None = None

    for _ in range(_MAX_JSON_ATTEMPTS):
        raw, pt, ct = await llm_service._call_llm(
            [{"role": "user", "content": prompt}],
            expect_json=True,
            temperature=temperature,
        )
        prompt_tokens += pt
        completion_tokens += ct
        try:
            return _parse_llm_json(raw), prompt_tokens, completion_tokens
        except ValueError as e:
            logger.warning(f"Malformed exam JSON from LLM: {e}")
            last_error = e

    raise ValueError(f"LLM did not return valid JSON: {last_error}")


async def generate_exam_question(
    character: CharacterData,
    context: HistoricalContextData | None,
    asked_questions: List[str],
    used_sources: List[str],
) -> tuple[str, str, str, int, int]:
    """Returns (question, expected_answer, source_excerpt, prompt_tokens, completion_tokens)."""
    source = await _pick_source(character, context, used_sources)
    prompt = build_exam_question_prompt(character, context, source, asked_questions)

    parsed, prompt_tokens, completion_tokens = await _call_llm_for_json(prompt, _QUESTION_TEMPERATURE)
    return (
        _clean_text(parsed, "question"),
        _clean_text(parsed, "expectedAnswer"),
        source,
        prompt_tokens,
        completion_tokens,
    )


async def grade_exam_answer(
    character: CharacterData,
    question: str,
    expected_answer: str,
    source: str,
    answer: str,
) -> tuple[str, str, int, int]:
    """Returns (verdict, feedback, prompt_tokens, completion_tokens)."""
    prompt = build_exam_grading_prompt(character, question, expected_answer, source, answer)

    parsed, prompt_tokens, completion_tokens = await _call_llm_for_json(prompt, _GRADING_TEMPERATURE)
    verdict = _clean_text(parsed, "verdict").upper()
    if verdict not in _VERDICTS:
        raise ValueError(f"Unknown exam verdict from LLM: {verdict}")
    return verdict, _clean_text(parsed, "feedback"), prompt_tokens, completion_tokens
