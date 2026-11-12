"""Oral-exam mode: the character asks questions and grades the learner's answers."""

import json
import logging
import random
import re
from typing import List

from history_talk_ai.application.chat import service as llm_service
from history_talk_ai.application.prompting.prompt_builder import (
    build_exam_follow_up_prompt,
    build_exam_grading_prompt,
    build_exam_question_prompt,
)
from history_talk_ai.dataaccess.java_backend.character_schema import CharacterData
from history_talk_ai.dataaccess.java_backend.historical_context_schema import HistoricalContextData

logger = logging.getLogger(__name__)

# Web and mobile render the parts around this separator as separate chat bubbles.
BUBBLE_SEPARATOR = "\n---\n"

_QUESTION_TEMPERATURE = 0.5
_GRADING_TEMPERATURE = 0.1
_MAX_JSON_ATTEMPTS = 2
_SOURCE_CANDIDATES = 10
_FOLLOW_UP_CANDIDATES = 3
_MIN_CHUNK_CHARS = 80
_MAX_FALLBACK_SOURCE_CHARS = 4000

# INCORRECT comes first so it is not read as CORRECT.
_VERDICT_RE = re.compile(r"(INCORRECT|PARTIAL|CORRECT)")
_FEEDBACK_LABEL_RE = re.compile(r"\s*\**\s*(?:LỜI\s+)?NHẬN XÉT\s*\**\s*:\s*\**\s*", re.IGNORECASE)
_QUESTION_LABEL_RE = re.compile(r"\s*\**\s*CÂU HỎI\s*\**\s*:\s*\**\s*", re.IGNORECASE)
# The question is one line, so a new line or the answer marker ends it and the answer is never streamed.
_QUESTION_END_RE = re.compile(r"\n|\**\s*ĐÁP ÁN\s*\**\s*:", re.IGNORECASE)
_ANSWER_RE = re.compile(r"ĐÁP ÁN\s*\**\s*:\s*\**\s*(.+)", re.IGNORECASE | re.DOTALL)
_MISSING_ANSWER_KEY = "Xem tư liệu."
_LABEL_HOLD_CHARS = 12
_STOP_HOLD_CHARS = 16
_VERDICT_HOLD_CHARS = 80


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


def _words(text: str) -> set[str]:
    return {word for word in re.findall(r"\w+", text.lower()) if len(word) > 1}


def _pick_from_pool(pool: List[str], used_sources: List[str], focus: str | None) -> str | None:
    """
    An unused excerpt of the conversation the exam was started from. With a focus, the excerpts sharing the most
    words with it come first, so the next question still follows on from the learner's answer.
    """
    unused = [source for source in pool if source not in used_sources]
    if not unused:
        return None
    if focus:
        focus_words = _words(focus)
        unused.sort(key=lambda source: len(focus_words & _words(source)), reverse=True)
        return random.choice(unused[:_FOLLOW_UP_CANDIDATES])
    return random.choice(unused)


async def _pick_source(
    character: CharacterData,
    context: HistoricalContextData | None,
    used_sources: List[str],
    focus: str | None = None,
    source_pool: List[str] | None = None,
) -> str:
    """
    Pick an unused document chunk, falling back to the character's background text.
    With a focus, the closest chunks to it are preferred so the question follows on from it.
    An exam started from a chat asks about that chat's excerpts first, then about the rest of the documents.
    """
    from_pool = _pick_from_pool(source_pool or [], used_sources, focus)
    if from_pool:
        return from_pool

    query = f"{character.name} {context.name}" if context else character.name
    if focus:
        query = f"{query} {focus}"
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
    candidates = unused or chunks
    if candidates:
        # Chunks come back closest first.
        return random.choice(candidates[:_FOLLOW_UP_CANDIDATES] if focus else candidates)

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
    source_pool: List[str] | None = None,
) -> tuple[str, str, str, int, int]:
    """Returns (question, expected_answer, source_excerpt, prompt_tokens, completion_tokens)."""
    source = await _pick_source(character, context, used_sources, source_pool=source_pool)
    prompt = build_exam_question_prompt(
        character, context, source, asked_questions, from_conversation=source in (source_pool or [])
    )

    parsed, prompt_tokens, completion_tokens = await _call_llm_for_json(prompt, _QUESTION_TEMPERATURE)
    return (
        _clean_text(parsed, "question"),
        _clean_text(parsed, "expectedAnswer"),
        source,
        prompt_tokens,
        completion_tokens,
    )


def _visible_part(full: str, label_re: re.Pattern, stop_re: re.Pattern | None, final: bool) -> str:
    """The part of a streamed reply the learner may see: without its label, and cut before stop_re."""
    if not final and len(full.lstrip()) < _LABEL_HOLD_CHARS:
        return ""
    label = label_re.match(full)
    text = full[label.end():] if label else full.lstrip()

    stop = stop_re.search(text) if stop_re else None
    if stop:
        return text[:stop.start()].rstrip()
    if final:
        return text.rstrip()
    # The answer marker may still be arriving, so the tail is held back until it can be ruled out.
    return text[:max(0, len(text) - _STOP_HOLD_CHARS)] if stop_re else text


async def _stream_feedback(prompt: str):
    """Yields feedback text chunks, then a dict with the verdict, the full feedback and token counts."""
    buffer = ""
    verdict = None
    body = ""
    emitted = ""
    tokens = {"prompt_tokens": 0, "completion_tokens": 0}

    async for chunk in llm_service._call_llm_stream(
        [{"role": "user", "content": prompt}], temperature=_GRADING_TEMPERATURE
    ):
        if isinstance(chunk, dict):
            tokens = chunk
            continue
        if verdict is None:
            buffer += chunk
            split = _split_verdict(buffer, final=False)
            if split is None:
                continue
            verdict, body = split
        else:
            body += chunk

        visible = _visible_part(body, _FEEDBACK_LABEL_RE, None, final=False)
        if len(visible) > len(emitted):
            yield visible[len(emitted):]
            emitted = visible

    if verdict is None:
        verdict, body = _split_verdict(buffer, final=True)
    feedback = _visible_part(body, _FEEDBACK_LABEL_RE, None, final=True)
    if not feedback:
        raise ValueError("LLM returned a verdict without feedback")
    if len(feedback) > len(emitted):
        yield feedback[len(emitted):]

    yield {"verdict": verdict, "feedback": feedback, **tokens}


def _split_verdict(buffer: str, final: bool) -> tuple[str, str] | None:
    """
    Splits "KẾT QUẢ: CORRECT\\nnhận xét" into the verdict and the feedback.
    Returns None while the header may still be arriving.
    """
    match = _VERDICT_RE.search(buffer)
    if not match:
        if final or len(buffer) >= _VERDICT_HOLD_CHARS:
            raise ValueError(f"No exam verdict in LLM output: {buffer[:200]}")
        return None
    rest = buffer[match.end():]
    if "\n" not in rest and not final and len(buffer) < _VERDICT_HOLD_CHARS:
        return None
    return match.group(1), rest.lstrip(" :.-*\n")


async def _stream_follow_up_question(prompt: str):
    """Yields question text chunks, then a dict with the question, its hidden answer and token counts."""
    full = ""
    emitted = ""
    tokens = {"prompt_tokens": 0, "completion_tokens": 0}

    async for chunk in llm_service._call_llm_stream(
        [{"role": "user", "content": prompt}], temperature=_QUESTION_TEMPERATURE
    ):
        if isinstance(chunk, dict):
            tokens = chunk
            continue
        full += chunk
        visible = _visible_part(full, _QUESTION_LABEL_RE, _QUESTION_END_RE, final=False)
        if len(visible) > len(emitted):
            yield visible[len(emitted):]
            emitted = visible

    question = _visible_part(full, _QUESTION_LABEL_RE, _QUESTION_END_RE, final=True)
    if not question:
        raise ValueError(f"No exam question in LLM output: {full[:200]}")
    if len(question) > len(emitted):
        yield question[len(emitted):]

    answer = _ANSWER_RE.search(full)
    expected_answer = answer.group(1).strip() if answer else ""
    yield {
        "question": question,
        "expectedAnswer": expected_answer or _MISSING_ANSWER_KEY,
        **tokens,
    }


async def stream_exam_turn(
    character: CharacterData,
    context: HistoricalContextData | None,
    question: str,
    expected_answer: str,
    source: str,
    answer: str,
    asked_questions: List[str],
    used_sources: List[str],
    ask_next: bool,
    source_pool: List[str] | None = None,
):
    """
    Grades the answer and, unless the exam is over, asks a follow-up question built on that answer.

    Yields visible text chunks (feedback, then "\\n---\\n" and the next question) and finally a dict
    with the verdict and the next answer key, which only the Java backend may see.
    """
    prompt_tokens = 0
    completion_tokens = 0

    grading_prompt = build_exam_grading_prompt(character, question, expected_answer, source, answer)
    graded: dict = {}
    async for chunk in _stream_feedback(grading_prompt):
        if isinstance(chunk, dict):
            graded = chunk
        else:
            yield chunk
    prompt_tokens += graded["prompt_tokens"]
    completion_tokens += graded["completion_tokens"]

    result = {"verdict": graded["verdict"], "feedback": graded["feedback"]}

    if ask_next:
        # Searching with the learner's own words makes the next question continue from what they said.
        next_source = await _pick_source(
            character, context, used_sources + [source], focus=f"{question} {answer}", source_pool=source_pool
        )
        follow_up_prompt = build_exam_follow_up_prompt(
            character, context, next_source, asked_questions, question, answer, graded["verdict"],
            from_conversation=next_source in (source_pool or []),
        )
        asked: dict = {}
        separator_sent = False
        async for chunk in _stream_follow_up_question(follow_up_prompt):
            if isinstance(chunk, dict):
                asked = chunk
                continue
            if not separator_sent:
                yield BUBBLE_SEPARATOR
                separator_sent = True
            yield chunk
        prompt_tokens += asked["prompt_tokens"]
        completion_tokens += asked["completion_tokens"]
        result.update(question=asked["question"], expectedAnswer=asked["expectedAnswer"], sourceExcerpt=next_source)

    yield {**result, "promptTokens": prompt_tokens, "completionTokens": completion_tokens}
