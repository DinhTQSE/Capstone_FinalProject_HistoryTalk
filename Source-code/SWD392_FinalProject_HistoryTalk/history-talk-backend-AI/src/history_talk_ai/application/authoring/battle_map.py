"""
Battle maps: the LLM plans factions, landmarks, phases and symbols in text; the editor turns them into
coordinates once the author has placed the landmarks on the map image. A vision model can suggest where the
landmarks are, but never draws the symbols.
"""

import base64
import io
import json
import logging
from typing import Literal

from PIL import Image
from pydantic import BaseModel, ConfigDict, Field

from history_talk_ai.application.authoring.jobs import AuthoringInputError, Progress
from history_talk_ai.application.authoring.sources import format_sources, keep_known_sources, retrieve_sources
from history_talk_ai.application.authoring.structured import generate_structured
from history_talk_ai.application.chat import service as llm_service
from history_talk_ai.application.chat.exam_service import _parse_llm_json
from history_talk_ai.application.prompting.authoring_prompts import build_battle_prompts, build_locate_prompt

logger = logging.getLogger(__name__)

PLAN_TEMPERATURE = 0.4
PLAN_MAX_TOKENS = 5000
MAX_SYMBOLS = 40

MOVEMENT_TYPES = {"arrow", "flank", "march", "retreat"}
PLACED_TYPES = {
    "defenseLine", "stakes",
    "infantry", "cavalry", "archer", "artillery", "armor", "navy", "airforce",
    "headquarters", "fort", "camp", "ambush",
    "clash", "victory", "destroyed",
    "label",
}

# Locating landmarks reads the map, so the image is shrunk only enough to keep the request fast.
_MAX_IMAGE_SIDE = 1280
_MAX_IMAGE_BYTES = 8 * 1024 * 1024


class Faction(BaseModel):
    id: str
    name: str
    side: Literal["ally", "enemy", "neutral"] = "neutral"


class Anchor(BaseModel):
    id: str
    name: str
    hint: str = ""
    kind: Literal["point", "area"] = "point"


class Symbol(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    type: str
    faction: str
    at: str | None = None
    from_: str | None = Field(default=None, alias="from")
    to: str | None = None
    via: str | None = None
    label: str | None = Field(default=None, max_length=60)


class Phase(BaseModel):
    title: str = Field(max_length=60)
    narration: str = Field(max_length=600)
    sources: list[str] = []
    camera: Literal["auto", "full"] = "auto"
    symbols: list[Symbol] = []


class BattlePlan(BaseModel):
    factions: list[Faction]
    anchors: list[Anchor]
    phases: list[Phase]


def context_year(context: dict) -> int | None:
    year = context.get("year") or context.get("startYear")
    if year is None:
        return None
    return -year if context.get("isBC") else year


def forbidden_types(year: int | None) -> set[str]:
    """Arms that did not exist yet in the battle's time."""
    if year is None:
        return set()
    forbidden = set()
    if year < 1900:
        forbidden |= {"armor", "airforce"}
    if year < 1400:
        forbidden.add("artillery")
    return forbidden


def era_rule(year: int | None) -> str:
    forbidden = forbidden_types(year)
    if not forbidden:
        return ""
    return f"- Trận này diễn ra trước khi có các binh chủng sau, KHÔNG được dùng: {', '.join(sorted(forbidden))}."


def _symbol_errors(where: str, symbol: Symbol, anchor_ids: set[str], faction_ids: set[str], forbidden: set[str]) -> list[str]:
    errors = []
    if symbol.type in MOVEMENT_TYPES:
        if not symbol.from_ or not symbol.to:
            errors.append(f"{where}: {symbol.type} cần có cả from và to")
        elif symbol.from_ == symbol.to:
            errors.append(f"{where}: {symbol.type} có from trùng to")
        refs = [("from", symbol.from_), ("to", symbol.to), ("via", symbol.via)]
    elif symbol.type in PLACED_TYPES:
        if not symbol.at:
            errors.append(f"{where}: {symbol.type} cần có at")
        refs = [("at", symbol.at)]
    else:
        return [f"{where}: loại ký hiệu '{symbol.type}' không hợp lệ"]

    for field, anchor_id in refs:
        if anchor_id is not None and anchor_id not in anchor_ids:
            errors.append(f"{where}: {field} trỏ tới điểm mốc '{anchor_id}' không tồn tại")
    if symbol.faction not in faction_ids:
        errors.append(f"{where}: faction '{symbol.faction}' không tồn tại")
    if symbol.type in forbidden:
        errors.append(f"{where}: trận này chưa có {symbol.type}")
    return errors


def validate_plan(
    plan: BattlePlan,
    options: dict,
    existing_factions: list[dict],
    known_sources: set[str],
    year: int | None,
) -> tuple[list[str], list[str]]:
    errors: list[str] = []
    warnings: list[str] = []

    for phase in plan.phases:
        phase.sources = keep_known_sources(phase.sources, known_sources)

    faction_ids = [f.id for f in plan.factions]
    if not 2 <= len(plan.factions) <= 4:
        errors.append(f"Cần 2–4 phe, đang có {len(plan.factions)}")
    if len(set(faction_ids)) < len(faction_ids):
        errors.append("Trùng id phe")
    existing_by_name = {f["name"].strip().lower(): f["id"] for f in existing_factions}
    for faction in plan.factions:
        kept_id = existing_by_name.get(faction.name.strip().lower())
        if kept_id is not None and kept_id != faction.id:
            errors.append(f"Phe '{faction.name}' đã có, phải giữ id '{kept_id}'")

    anchor_ids = [a.id for a in plan.anchors]
    if not 3 <= len(plan.anchors) <= 10:
        errors.append(f"Cần 3–10 điểm mốc, đang có {len(plan.anchors)}")
    if len(set(anchor_ids)) < len(anchor_ids):
        errors.append("Trùng id điểm mốc")

    wanted = options.get("phaseCount") or 5
    if abs(len(plan.phases) - wanted) > 1:
        errors.append(f"Cần khoảng {wanted} giai đoạn, đang có {len(plan.phases)}")

    forbidden = forbidden_types(year)
    for index, phase in enumerate(plan.phases, start=1):
        if not 1 <= len(phase.symbols) <= 6:
            errors.append(f"Giai đoạn {index}: cần 1–6 ký hiệu, đang có {len(phase.symbols)}")
        for number, symbol in enumerate(phase.symbols, start=1):
            errors.extend(_symbol_errors(
                f"Giai đoạn {index}, ký hiệu {number}", symbol, set(anchor_ids), set(faction_ids), forbidden
            ))

    total = sum(len(phase.symbols) for phase in plan.phases)
    if total > MAX_SYMBOLS:
        errors.append(f"Tổng số ký hiệu tối đa {MAX_SYMBOLS}, đang có {total}")

    used = {ref for phase in plan.phases for s in phase.symbols for ref in (s.at, s.from_, s.to, s.via) if ref}
    unused = [a.id for a in plan.anchors if a.id not in used]
    if unused:
        plan.anchors = [a for a in plan.anchors if a.id in used]
        warnings.append(f"Đã bỏ các điểm mốc không có ký hiệu nào dùng tới: {', '.join(unused)}")

    return errors, warnings


async def plan_battle_map(
    context: dict,
    characters: list[dict],
    options: dict,
    existing_factions: list[dict],
    existing_anchors: list[dict],
    story_summary: str | None,
    progress: Progress,
) -> dict:
    progress.update("retrieving", 10)
    entity_ids = [context["contextId"]] + [c["characterId"] for c in characters]
    query = " ".join(p for p in (context.get("name"), context.get("description"), options.get("notes")) if p)
    chunks = await retrieve_sources(query[:2000], entity_ids)

    year = context_year(context)
    system, user = build_battle_prompts(
        context, options, existing_factions, existing_anchors, story_summary, era_rule(year), format_sources(chunks)
    )
    known = {chunk.id for chunk in chunks}

    def check(plan: BattlePlan):
        return validate_plan(plan, options, existing_factions, known, year)

    result = await generate_structured(
        system, user, BattlePlan, check, progress, temperature=PLAN_TEMPERATURE, max_tokens=PLAN_MAX_TOKENS
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


# ── Locating landmarks on the map image ──────────────────────────────────────


async def _load_image(image_url: str | None, image_base64: str | None) -> bytes:
    if image_base64:
        raw = image_base64.split(",", 1)[1] if image_base64.lstrip().startswith("data:") else image_base64
        try:
            return base64.b64decode("".join(raw.split()), validate=True)
        except ValueError as exc:
            raise AuthoringInputError("Ảnh base64 không hợp lệ.") from exc
    if image_url:
        try:
            response = await llm_service.ollama_client.get(image_url, timeout=20.0)
            response.raise_for_status()
        except Exception as exc:
            raise AuthoringInputError("Không tải được ảnh bản đồ từ đường dẫn.") from exc
        if len(response.content) > _MAX_IMAGE_BYTES:
            raise AuthoringInputError("Ảnh bản đồ quá lớn.")
        return response.content
    raise AuthoringInputError("Cần imageUrl hoặc imageBase64.")


def _prepare_image(data: bytes) -> tuple[str, int, int]:
    """Returns the base64 JPEG sent to the model and the size the model sees."""
    try:
        image = Image.open(io.BytesIO(data))
        image.load()
    except Exception as exc:
        raise AuthoringInputError("File không phải ảnh hợp lệ.") from exc
    image = image.convert("RGB")
    image.thumbnail((_MAX_IMAGE_SIDE, _MAX_IMAGE_SIDE))
    buffer = io.BytesIO()
    image.save(buffer, format="JPEG", quality=90)
    return base64.b64encode(buffer.getvalue()).decode(), image.width, image.height


def to_percent(point, width: int, height: int) -> tuple[float, float] | None:
    """
    The model is asked for 0–1000 coordinates, but Qwen vision models sometimes answer in pixels of the image
    they were given. Values above 1000 can only be pixels.
    """
    if not isinstance(point, (list, tuple)) or len(point) != 2:
        return None
    try:
        x, y = float(point[0]), float(point[1])
    except (TypeError, ValueError):
        return None
    if x < 0 or y < 0:
        return None
    if max(x, y) <= 1000:
        px, py = x / 10, y / 10
    else:
        px, py = x / width * 100, y / height * 100
    if px > 100 or py > 100:
        return None
    return round(px, 1), round(py, 1)


async def locate_anchors(image_url: str | None, image_base64: str | None, anchors: list[dict]) -> dict:
    image_b64, width, height = _prepare_image(await _load_image(image_url, image_base64))

    vision_user = llm_service.settings.VISION_OLLAMA_USERNAME or llm_service.settings.OLLAMA_USERNAME
    vision_pass = llm_service.settings.VISION_OLLAMA_PASSWORD or llm_service.settings.OLLAMA_PASSWORD
    payload = {
        "model": llm_service.settings.LOCATE_MODEL,
        "messages": [{"role": "user", "content": build_locate_prompt(anchors), "images": [image_b64]}],
        "stream": False,
        "think": False,
        "format": "json",
        "options": {"temperature": 0},
    }
    response = await llm_service.ollama_client.post(
        llm_service._vision_ollama_url("/api/chat"),
        json=payload,
        auth=(vision_user, vision_pass) if vision_user else None,
        timeout=180.0,
    )
    response.raise_for_status()
    content = response.json().get("message", {}).get("content", "")
    try:
        found = {item.get("id"): item for item in _parse_llm_json(content).get("anchors", []) if isinstance(item, dict)}
    except (ValueError, json.JSONDecodeError):
        logger.warning(f"Locate model did not return JSON: {content[:200]}")
        found = {}

    results = []
    for anchor in anchors:
        item = found.get(anchor["id"]) or {}
        position = to_percent(item.get("point"), width, height) if item.get("found") else None
        if position is None:
            results.append({"id": anchor["id"], "found": False})
            continue
        try:
            confidence = min(1.0, max(0.0, float(item.get("confidence", 0.5))))
        except (TypeError, ValueError):
            confidence = 0.5
        results.append({"id": anchor["id"], "found": True, "x": position[0], "y": position[1], "confidence": confidence})
    return {"anchors": results}
