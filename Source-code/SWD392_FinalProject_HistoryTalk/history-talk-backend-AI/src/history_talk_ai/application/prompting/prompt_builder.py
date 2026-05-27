"""Builds system prompts from character and historical-context data."""

from history_talk_ai.dataaccess.java_backend.character_schema import CharacterData
from history_talk_ai.dataaccess.java_backend.historical_context_schema import HistoricalContextData

_CHAT_SYSTEM_TEMPLATE = """\
Bạn là **{name}**, {title_line}nhân vật lịch sử Việt Nam thời {era}.

[THÔNG TIN NHÂN VẬT & BỐI CẢNH]
- Tiểu sử: {background}
- Tính cách: {personality}
- Sự kiện tham gia: {context_name} ({year_label})
- Bối cảnh sự kiện: {context_description}

[QUY TẮC]
1. Xưng hô và nói chuyện chuẩn mực theo đúng tư cách {name}. Tuyệt đối không nhận là AI hay trợ lý ảo.
2. Trả lời vô cùng NGẮN GỌN, SÚC TÍCH (1-3 câu), đi thẳng vào trọng tâm câu hỏi.
3. NẾU LỊCH SỬ KHÔNG CÓ HOẶC BẠN KHÔNG RÕ, PHẢI THỪA NHẬN LÀ KHÔNG NHỚ HOẶC KHÔNG BIẾT. KHÔNG ĐƯỢC BỊA ĐẶT, SUY DIỄN HAY SÁNG TÁC THÔNG TIN HƯ CẤU.
"""

_TITLE_SYSTEM_TEMPLATE = """\
Bạn là trợ lý tạo tiêu đề ngắn gọn cho cuộc hội thoại.
Tạo một tiêu đề không quá 8 từ bằng tiếng Việt dựa trên cặp tin nhắn đầu tiên sau đây.
Tiêu đề phải thể hiện chủ đề chính của cuộc trò chuyện với nhân vật {name}.
Chỉ trả về tiêu đề, không thêm dấu ngoặc kép hay giải thích.\
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

    return _CHAT_SYSTEM_TEMPLATE.format(
        name=_esc(character.name),
        title_line=_esc(title_line),
        background=_esc(character.background),
        personality=_esc(character.personality or "Không rõ"),
        context_name=_esc(context.name),
        context_description=_esc(context.description),
        era=_translate_era(context.era),
        year_label=_esc(year_label)
    )


def build_title_system_prompt(character: CharacterData) -> str:
    return _TITLE_SYSTEM_TEMPLATE.format(name=character.name)


# ── Helpers ───────────────────────────────────────────────────────────────────

_ERA_MAP = {
    "ANCIENT": "Cổ đại",
    "MEDIEVAL": "Trung đại",
    "MODERN": "Cận đại",
    "CONTEMPORARY": "Hiện đại",
}

def _translate_era(era: str | None) -> str:
    return _ERA_MAP.get(era or "", era or "Không rõ")


def _resolve_year_label(context: HistoricalContextData) -> str:
    if context.yearLabel:
        return context.yearLabel
    suffix = " TCN" if context.isBC else ""
    if context.startYear and context.endYear:
        return f"{context.startYear}–{context.endYear}{suffix}"
    if context.year:
        return f"{context.year}{suffix}"
    return "Không rõ"
