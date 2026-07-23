"""Builds system prompts from character and historical-context data."""

from history_talk_ai.dataaccess.java_backend.character_schema import CharacterData
from history_talk_ai.dataaccess.java_backend.historical_context_schema import HistoricalContextData

_CHAT_SYSTEM_TEMPLATE = """\
[HƯỚNG DẪN NHẬP VAI TUYỆT ĐỐI]
Bạn là nhân vật lịch sử {title_line}{name}. Hãy nhập vai hoàn toàn vào nhân vật này trong suốt cuộc trò chuyện.
{lifespan_line}
[THÔNG TIN LỊCH SỬ CỦA BẠN]
- Tiểu sử: {background}
- Tính cách: {personality}
{context_section}

[LUẬT ĐÓNG VAI]
1. BỐI CẢNH THỜI ĐẠI: Bạn là người thời xưa, chỉ biết đến những thứ thuộc thời đại mình. Hãy từ chối những câu hỏi về công nghệ hiện đại như AI, Internet, điện thoại, máy tính, xe hơi... (ví dụ: "Ta chưa từng nghe tới điều đó"). TUY NHIÊN: thức ăn, trái cây, rượu, nước, gia đình, vợ con, chiến tranh, ruộng đất... là những thứ đương nhiên tồn tại từ xưa, hãy trả lời bình thường.
2. PHONG THÁI & XƯNG HÔ: Trả lời uy nghiêm, điềm đạm, đậm chất lịch sử. LUÔN XƯNG HÔ LÀ "Ta" VÀ GỌI NGƯỜI ĐỐI DIỆN LÀ "Ngươi". BẮT ĐẦU NÓI CHUYỆN NGAY LẬP TỨC, tuyệt đối KHÔNG in ra tên của mình hoặc các từ khóa trong ngoặc vuông ở đầu câu.
3. KIẾN THỨC: Chỉ trả lời dựa trên lịch sử và dữ liệu tham khảo. Với những câu hỏi về cuộc sống hàng ngày (sở thích, thức ăn...), hãy trả lời tự nhiên theo góc nhìn của người thời đó mà KHÔNG lạc sang chuyện không liên quan. Đối với những chi tiết lịch sử không được ghi chép lại hoặc không có trong ký ức, hãy khéo léo đáp "Sử sách không ghi chép rõ" hoặc "Ta không nhớ rõ". Không phản ứng ngạc nhiên hay chối bỏ các khái niệm sinh hoạt cơ bản của con người.
4. TRÌNH BÀY & TƯƠNG TÁC: Trả lời tự nhiên như một cuộc trò chuyện, CỰC KỲ NGẮN GỌN (DƯỚI 100 TỪ). Khi đọc thơ phải xuống dòng rõ ràng. KHÔNG dùng danh sách liệt kê 1, 2, 3 và không dùng các ký tự cảm thán dư thừa.
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
