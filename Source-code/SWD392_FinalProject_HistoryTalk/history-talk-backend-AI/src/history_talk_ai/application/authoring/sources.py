"""Document excerpts a draft is written from, each with an id the model cites in "sources"."""

import logging
from dataclasses import dataclass
from typing import List

from history_talk_ai.application.authoring.jobs import AuthoringInputError
from history_talk_ai.application.chat import service as llm_service

logger = logging.getLogger(__name__)

MAX_SOURCES = 12


@dataclass(frozen=True)
class SourceChunk:
    id: str
    content: str


def chunk_label(chunk_id: str) -> str:
    """A short id that stays the same for the same chunk, e.g. chunk_1a2b3c4d."""
    return "chunk_" + str(chunk_id).replace("-", "")[:8]


async def retrieve_sources(query: str, entity_ids: List[str], limit: int = MAX_SOURCES) -> List[SourceChunk]:
    """Closest chunks of the given contexts and characters. Without any, the model would have to invent."""
    vector = await llm_service.get_embedding_from_ollama(query)
    if not vector:
        raise llm_service.RagUnavailableError("Không tra cứu được tài liệu. Vui lòng thử lại sau.")
    try:
        response = llm_service.supabase.schema(llm_service.settings.SUPABASE_SCHEMA).rpc(
            "match_history_chunks",
            {"query_embedding": vector, "match_limit": limit, "filter_entity_ids": entity_ids},
        ).execute()
    except Exception as exc:
        logger.error(f"Supabase RPC error while retrieving authoring sources: {exc}")
        raise llm_service.RagUnavailableError("Không tra cứu được tài liệu. Vui lòng thử lại sau.") from exc

    chunks: List[SourceChunk] = []
    seen: set[str] = set()
    for row in response.data or []:
        content = (row.get("content") or "").strip()
        if not content or not row.get("chunk_id"):
            continue
        label = chunk_label(row["chunk_id"])
        if label not in seen:
            seen.add(label)
            chunks.append(SourceChunk(label, content))

    if not chunks:
        raise AuthoringInputError("Bối cảnh chưa có tài liệu tham khảo.")
    return chunks


def format_sources(chunks: List[SourceChunk]) -> str:
    return "\n\n".join(f"[{chunk.id}] {chunk.content}" for chunk in chunks)


def keep_known_sources(sources: list, known_ids: set[str]) -> list[str]:
    """Drops ids the model made up, so a citation always points at a real excerpt."""
    return [source for source in sources or [] if isinstance(source, str) and source in known_ids]
