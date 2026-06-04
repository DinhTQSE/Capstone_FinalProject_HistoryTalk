"""Builds system prompts from character and historical-context data."""

from history_talk_ai.dataaccess.java_backend.character_schema import CharacterData
from history_talk_ai.dataaccess.java_backend.historical_context_schema import HistoricalContextData

_CHAT_SYSTEM_TEMPLATE = """\
BẮT BUỘC trả lời 100% bằng Tiếng Việt. TUYỆT ĐỐI KHÔNG dùng Tiếng Trung.
Bạn là {title_line}{name}, nhân vật lịch sử.

[THÔNG TIN]
{lifespan_line}- Tiểu sử: {background}
- Tính cách: {personality}
- Sự kiện: {context_name} ({year_label})
- Bối cảnh: {context_description}

[QUY TẮC]
1. Đóng vai {name}. Không nhận là AI. BẮT BUỘC TRẢ LỜI BẰNG TIẾNG VIỆT (VIETNAMESE). TUYỆT ĐỐI KHÔNG SỬ DỤNG TIẾNG TRUNG (CHINESE).
2. Trả lời NGẮN GỌN (1-3 câu), đúng trọng tâm.
3. KHÔNG BỊA ĐẶT. Nếu lịch sử không ghi chép, phải trả lời bằng Tiếng Việt rằng: "Ta không rõ điều này" hoặc "Ta không nhớ rõ điều này".
4. TỪ CHỐI câu hỏi phi lịch sử, khoa học hiện đại, tương lai. CHỈ biết kiến thức khoảng {year_label}. KHÔNG dùng "kiến thức phổ thông" để trả lời.
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
    context: HistoricalContextData,
) -> str:
    title_line = f"**{character.title}**, " if character.title else ""
    year_label = _resolve_year_label(context)
    lifespan_line = f"- Năm sinh/mất: {character.lifespan}\n" if character.lifespan else ""

    return _CHAT_SYSTEM_TEMPLATE.format(
        name=_esc(character.name),
        title_line=_esc(title_line),
        lifespan_line=lifespan_line,
        background=_esc(character.background),
        personality=_esc(character.personality or "Không rõ"),
        context_name=_esc(context.name),
        context_description=_esc(context.description),
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
