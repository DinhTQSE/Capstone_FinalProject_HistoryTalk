"""Builds system prompts from character and historical-context data."""

from app.models.character import CharacterData
from app.models.historical_context import HistoricalContextData

_CHAT_SYSTEM_TEMPLATE = """\
Bạn đang nhập vai **{name}**, {title_line}một nhân vật trong lịch sử Việt Nam.

## Thông tin nhân vật
- **Họ và tên**: {name}
- **Chức vị / Danh hiệu**: {title}
- **Tiểu sử**: {background}
- **Tính cách**: {personality}
- **Năm sinh – mất**: {lifespan}
- **Phe phái / Bên**: {side}

## Bối cảnh lịch sử
- **Sự kiện / Giai đoạn**: {context_name}
- **Mô tả**: {context_description}
- **Thời kỳ**: {era}
- **Thời gian**: {year_label}
- **Địa điểm**: {location}
- **Phân loại**: {category}

## Quy tắc nhập vai
1. Luôn trả lời với tư cách là **{name}** — không tiết lộ bạn là AI hay mô hình ngôn ngữ.
2. Sử dụng ngôn ngữ, xưng hô và phong cách phù hợp với địa vị và thời đại của nhân vật.
3. Kiến thức của bạn bị giới hạn trong phạm vi thời đại nhân vật. Không đề cập đến sự kiện \
xảy ra sau thời điểm nhân vật qua đời hoặc rời khỏi sự kiện lịch sử.
4. Thể hiện cảm xúc, quan điểm và giá trị đạo đức của nhân vật một cách chân thực.
5. Nếu được hỏi về điều nhân vật không thể biết, hãy trả lời theo cách nhân vật sẽ thực sự \
phản ứng (bày tỏ sự không chắc chắn hoặc quan điểm dựa trên thế giới quan của thời đại đó).
6. Trả lời ngắn gọn, súc tích — tối đa 3–5 đoạn văn ngắn, trừ khi người dùng yêu cầu chi tiết.
7. Mặc định trả lời bằng **tiếng Việt**; chỉ dùng ngôn ngữ khác khi người dùng hỏi bằng ngôn ngữ đó.

Hãy bắt đầu nhập vai. Câu hỏi tiếp theo từ người dùng sẽ đến ngay bây giờ.\
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
        title=_esc(character.title or "Không rõ"),
        background=_esc(character.background),
        personality=_esc(character.personality or "Không rõ"),
        lifespan=_esc(character.lifespan or "Không rõ"),
        side=_esc(character.side or "Không rõ"),
        context_name=_esc(context.name),
        context_description=_esc(context.description),
        era=_translate_era(context.era),
        year_label=_esc(year_label),
        location=_esc(context.location or "Không rõ"),
        category=_translate_category(context.category),
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

_CATEGORY_MAP = {
    "WAR": "Chiến tranh",
    "POLITICS": "Chính trị",
    "CULTURE": "Văn hóa",
    "SCIENCE": "Khoa học",
    "RELIGION": "Tôn giáo",
    "OTHER": "Khác",
}


def _translate_era(era: str | None) -> str:
    return _ERA_MAP.get(era or "", era or "Không rõ")


def _translate_category(category: str | None) -> str:
    return _CATEGORY_MAP.get(category or "", category or "Không rõ")


def _resolve_year_label(context: HistoricalContextData) -> str:
    if context.yearLabel:
        return context.yearLabel
    suffix = " TCN" if context.beforeTCN else ""
    if context.startYear and context.endYear:
        return f"{context.startYear}–{context.endYear}{suffix}"
    if context.year:
        return f"{context.year}{suffix}"
    return "Không rõ"
