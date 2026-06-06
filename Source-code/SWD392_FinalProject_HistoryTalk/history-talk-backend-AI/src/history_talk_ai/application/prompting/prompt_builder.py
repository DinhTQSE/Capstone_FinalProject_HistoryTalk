"""Builds system prompts from character and historical-context data."""

from history_talk_ai.dataaccess.java_backend.character_schema import CharacterData
from history_talk_ai.dataaccess.java_backend.historical_context_schema import HistoricalContextData

_CHAT_SYSTEM_TEMPLATE = """\
Đóng vai: {title_line}{name}.
{lifespan_line}Tiểu sử: {background}
Tính cách: {personality}
{context_section}
LUẬT:
1. Trả lời bằng 100% TIẾNG VIỆT. CẤM dùng ngôn ngữ khác.
2. Ngắn gọn (1-3 câu). Không nhận là AI.
3. CHỈ nói phần mình. CẤM tự ý đặt câu hỏi thay người dùng.
4. Từ chối câu hỏi hiện đại/tương lai (chỉ biết năm {year_label}).
5. Không bịa đặt. Không biết thì nói "Ta không rõ" hoặc "Ta không nhớ rõ".
"""

_TITLE_SYSTEM_TEMPLATE = """\
Tạo tiêu đề hội thoại với {name} (dưới 8 từ).
Không giải thích, chỉ trả về tiêu đề.\
"""


def _esc(value: str) -> str:
    """Escape curly braces in user-supplied text so .format() won't misparse them."""
    return value.replace("{", "{{").replace("}", "}}")


def build_chat_system_prompt(
    character: CharacterData,
    context: HistoricalContextData | None,
) -> str:
    title_line = f"**{character.title}**, " if character.title else ""
    lifespan_line = f"- Năm sinh/mất: {character.lifespan}\n" if character.lifespan else ""

    if context:
        year_label = _resolve_year_label(context)
        context_section = (
            f"- Sự kiện: {_esc(context.name)} ({_esc(year_label)})\n"
            f"- Bối cảnh: {_esc(context.description)}"
        )
    else:
        year_label = "thời đại của mình"
        context_section = "- Bối cảnh: Toàn bộ cuộc đời của nhân vật lịch sử này."

    return _CHAT_SYSTEM_TEMPLATE.format(
        name=_esc(character.name),
        title_line=_esc(title_line),
        lifespan_line=lifespan_line,
        background=_esc(character.background),
        personality=_esc(character.personality or "Không rõ"),
        context_section=context_section,
        year_label=_esc(year_label)
    )


def build_title_system_prompt(character: CharacterData) -> str:
    return _TITLE_SYSTEM_TEMPLATE.format(name=character.name)


# ── Helpers ───────────────────────────────────────────────────────────────────


def _resolve_year_label(context: HistoricalContextData) -> str:
    if context.yearLabel:
        return context.yearLabel
    suffix = " TCN" if context.isBC else ""
    if context.startYear and context.endYear:
        return f"{context.startYear}–{context.endYear}{suffix}"
    if context.year:
        return f"{context.year}{suffix}"
    return "Không rõ"
