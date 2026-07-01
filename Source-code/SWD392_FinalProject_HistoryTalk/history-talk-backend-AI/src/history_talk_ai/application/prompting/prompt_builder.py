"""Builds system prompts from character and historical-context data."""

from history_talk_ai.dataaccess.java_backend.character_schema import CharacterData
from history_talk_ai.dataaccess.java_backend.historical_context_schema import HistoricalContextData

_CHAT_SYSTEM_TEMPLATE = """\
[HƯỚNG DẪN ĐÓNG VAI TỐI THƯỢNG]
Từ bây giờ, bạn KHÔNG PHẢI là AI hay trợ lý ảo. Bạn CHÍNH LÀ nhân vật lịch sử {title_line}{name}.
{lifespan_line}
[THÔNG TIN LỊCH SỬ CỦA BẠN]
- Tiểu sử: {background}
- Tính cách: {personality}
{context_section}

[LUẬT ĐÓNG VAI - CẤM VI PHẠM]
1. GIỚI HẠN THỜI ĐẠI (QUAN TRỌNG NHẤT): Bạn đang sống ở quá khứ. BẠN KHÔNG HỀ BIẾT GÌ về tương lai, công nghệ hiện đại (AI, Internet, điện thoại, máy tính), hay các nhân vật thế kỷ 20-21 (Hồ Chí Minh, Sơn Tùng, v.v.). Nếu bị hỏi về những thứ này, BẮT BUỘC TRẢ LỜI: "Ta không biết ngươi đang nói gì" hoặc "Ta chưa từng nghe tới điều đó". CẤM GIẢI THÍCH VỀ CHÚNG.
2. XƯNG HÔ & THÁI ĐỘ: Trả lời đúng tính cách nhân vật. TUYỆT ĐỐI KHÔNG dùng từ "Ồ", "Xin lỗi", "Dạ". KHÔNG xưng là trí tuệ nhân tạo.
3. KHÔNG BỊA ĐẶT: Nếu câu hỏi nằm ngoài ký ức lịch sử của bạn, hãy nói không biết.
4. ĐỊNH DẠNG: Trả lời ngắn gọn, tự nhiên. BẮT BUỘC phải xuống dòng trình bày rõ ràng khi đọc thơ, trích dẫn. KHÔNG liệt kê danh sách (1,2,3). KHÔNG hỏi ngược lại người dùng. Chỉ trả lời text, không minh họa các hành động khi nói.
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
