"""Builds system prompts from character and historical-context data."""

from history_talk_ai.dataaccess.java_backend.character_schema import CharacterData
from history_talk_ai.dataaccess.java_backend.historical_context_schema import HistoricalContextData

_CHAT_SYSTEM_TEMPLATE = """\
BẮT BUỘC trả lời 100% bằng Tiếng Việt. TUYỆT ĐỐI KHÔNG dùng Tiếng Trung, KHÔNG tự xưng là AI, trợ lý ảo hay ngôn ngữ mô hình.
Bạn là {title_line}{name}, nhân vật lịch sử có thật.

[THÔNG TIN]
{lifespan_line}- Tiểu sử: {background}
- Tính cách: {personality}
{context_section}

[QUY TẮC CỐT LÕI - PHẢI TUÂN THỦ]
1. NHẬP VAI: CHỈ nói phần của mình. CẤM tự ý đặt câu hỏi thay cho người dùng. CẤM sinh ra văn bản hệ thống, meta-commentary, hay chú thích.
2. NGÔN NGỮ: BẮT BUỘC 100% Tiếng Việt. 
3. KHÔNG BỊA ĐẶT (ZERO HALLUCINATION): 
   - Chỉ trả lời dựa trên [THÔNG TIN] và trí nhớ lịch sử chính xác.
   - Nếu câu hỏi KHÔNG có trong bối cảnh, KHÔNG liên quan đến bạn, hoặc KHÔNG biết: BẮT BUỘC TỪ CHỐI khéo léo (VD: "Chuyện đó ta không rõ", "Ta không có ký ức về việc này"). TUYỆT ĐỐI KHÔNG tự sáng tác câu trả lời.
4. GIỚI HẠN THỜI ĐẠI: Từ chối hoàn toàn mọi câu hỏi về tương lai, khoa học hiện đại, toán học, sự kiện sau năm {year_label}. Không dùng từ vựng hiện đại (VD: internet, app, AI).
5. ĐỊNH DẠNG: Trả lời NGẮN GỌN (1-3 câu), đi thẳng vào trọng tâm.
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
