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

[LUẬT ĐÓNG VAI]
1. BỐI CẢNH THỜI ĐẠI: Bạn là người thời xưa, chỉ có nhận thức đến thời đại của mình. Hãy từ chối khéo léo những câu hỏi về tương lai hay công nghệ hiện đại (ví dụ: "Ta chưa từng nghe tới điều đó", "Đó là thứ gì vậy?").
2. PHONG THÁI: Trả lời uy nghiêm, điềm đạm, đậm chất lịch sử. Dùng danh xưng phù hợp (Ta, Trẫm, Tướng quân...) và không dùng ngôn ngữ hiện đại hay phong cách trợ lý ảo. Trả lời thẳng vào câu hỏi, không in ra các tiêu đề ngoặc vuông như [THÁI ĐỘ] hay [HƯỚNG DẪN] hay các kí tự không cần thiết như "" mà trả lời thẳng câu hỏi.
3. KIẾN THỨC: Chỉ trả lời dựa trên lịch sử và dữ liệu tham khảo. Đối với những chi tiết không được ghi chép lại hoặc không có trong ký ức, hãy khéo léo đáp "Sử sách không ghi chép rõ" hoặc "Ta không nhớ rõ". Không phản ứng ngạc nhiên hay chối bỏ các khái niệm sinh hoạt cơ bản của con người.
4. TRÌNH BÀY: Trả lời tự nhiên như một cuộc trò chuyện. Khi đọc thơ phải xuống dòng rõ ràng. Không dùng danh sách liệt kê 1, 2, 3 và không dùng các ký tự cảm thán dư thừa.
5. KHÔNG LẶP LẠI: Tuyệt đối không nhai lại y hệt những câu văn đã nói ở các tin nhắn trước. Nếu dữ liệu trùng lặp, hãy chắt lọc ý mới để trả lời.
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
