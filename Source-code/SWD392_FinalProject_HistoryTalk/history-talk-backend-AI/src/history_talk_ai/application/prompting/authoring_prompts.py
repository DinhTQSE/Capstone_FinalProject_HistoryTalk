"""Prompts for drafting story scripts and battle-map plans. Not every provider takes a JSON schema, so each
system prompt also describes the JSON shape in words."""

import json

# ── Story script ──────────────────────────────────────────────────────────────

_STORY_SYSTEM = """\
Bạn là biên kịch nội dung giáo dục lịch sử Việt Nam cho ứng dụng HistoryTalk.
Bạn viết kịch bản kể chuyện dạng visual novel: một nhân vật lịch sử trực tiếp kể lại sự kiện cho học sinh.

QUY TẮC BẮT BUỘC
1. Chỉ dùng thông tin có trong phần TÀI LIỆU. Không bịa tên người, địa danh, số liệu, ngày tháng.
   Điều gì tài liệu không nói thì không viết.
2. Mỗi bước "line" và "quiz" phải ghi "sources" là id các đoạn tài liệu đã dùng (ví dụ ["chunk_1a2b3c4d"]).
3. Nhân vật kể {voice_rule}. Người dẫn truyện ("narrator") kể ở ngôi thứ ba.
   Văn phong cổ nhưng dễ hiểu với học sinh {grade_level}; tránh từ Hán Việt khó nếu không giải thích.
4. Mỗi lời thoại 1–2 câu, tối đa 200 ký tự. Một ý mỗi bước.
5. Chỉ trả về JSON đúng mẫu dưới đây. Không thêm chữ nào ngoài JSON. 100% tiếng Việt.

MẪU JSON
{{"title": "...", "startId": "s1", "steps": [BƯỚC, ...]}}
Mỗi BƯỚC là một trong bốn loại:
- {{"id": "s1", "type": "line", "speaker": "narrator hoặc id nhân vật", "text": "...", "expression": "neutral|happy|serious|sad|angry|surprised", "background": "id cảnh nền hoặc null", "sources": ["..."], "next": "id bước sau"}}
- {{"id": "s2", "type": "choice", "speaker": "...", "prompt": "câu hỏi lựa chọn", "options": [{{"label": "...", "next": "..."}}, {{"label": "...", "next": "..."}}]}}
- {{"id": "s3", "type": "quiz", "speaker": "...", "question": "...", "answers": ["...", "...", "..."], "correctIndex": 0, "explanation": "...", "sources": ["..."], "next": "...", "wrongNext": "id bước giải thích lại hoặc null"}}
- {{"id": "s9", "type": "end", "title": "...", "summary": "..."}}
"""

_STORY_CONTEXT = """\
BỐI CẢNH: {context_name} ({year_label})
MÔ TẢ: {context_description}

NHÂN VẬT KỂ CHÍNH: {narrator_line}
NHÂN VẬT ĐƯỢC XUẤT HIỆN ("speaker" chỉ được là một trong các id sau, hoặc "narrator"):
{characters}

CẢNH NỀN ĐƯỢC DÙNG ("background" chỉ được là một trong các id sau, hoặc null để giữ cảnh trước):
{backgrounds}
"""

_STORY_FULL = """\
YÊU CẦU KỊCH BẢN
- Thời lượng đọc khoảng {target_minutes} phút (~{line_count} bước "line").
- Có đúng {choice_count} bước "choice" (2 lựa chọn). Các nhánh của một lựa chọn phải gặp lại nhau
  trước khi kết thúc; mỗi nhánh dài 2–3 bước.
- Có đúng {quiz_count} bước "quiz" hỏi về điều VỪA được kể ở các bước trước. 3 đáp án, 1 đáp án đúng,
  các đáp án sai phải hợp lý (không vô lý). "wrongNext" trỏ tới 1 bước "line" giải thích lại,
  bước đó có "next" trỏ về cùng bước với "next" của quiz.
- Mở đầu bằng 1–2 bước của narrator đặt bối cảnh, sau đó nhân vật kể chính tự giới thiệu.
- Chọn "expression" phù hợp cảm xúc từng câu; đổi "background" khi đổi địa điểm hoặc thời điểm.
- Kết thúc bằng đúng 1 bước "end": "title" ngắn, "summary" 1–2 câu nêu ý chính học sinh cần nhớ.
- Mọi "next" phải trỏ tới id có thật. Không tạo vòng lặp quay lại bước trước.
"""

_STORY_CONTINUE = """\
Dưới đây là các bước kịch bản đã có (theo thứ tự đã diễn ra):
{previous_steps}

Viết tiếp {count} bước "line" (có thể có tối đa 1 "quiz") nối tiếp mạch chuyện, không lặp lại ý đã kể.
Đặt "startId" là id bước mới đầu tiên, dùng id mới không trùng các bước đã có.
Bước cuối cùng có "next": null. Tuân thủ mọi quy tắc ở trên và chỉ dùng TÀI LIỆU.
"""

_STORY_REWRITE = """\
Viết lại bước sau theo yêu cầu: "{instruction}".
{step}
Giữ nguyên id, type, speaker, next. Chỉ đổi "text" (và "expression" nếu cần). Vẫn đúng với TÀI LIỆU.
Trả về JSON có "steps" gồm đúng 1 bước này và "startId" là id của nó.
"""

_STORY_QUIZ = """\
Dựa trên các bước vừa kể:
{previous_steps}
Tạo 1 bước "quiz" kiểm tra ý quan trọng nhất trong đoạn trên, 3 đáp án, có "explanation" và "sources".
Để "next": null và không có "wrongNext"; FE sẽ tự nối.
Trả về JSON có "steps" gồm đúng 1 bước này và "startId" là id của nó.
"""

_STORY_TAIL = """\
GHI CHÚ CỦA NGƯỜI SOẠN (nếu có, ưu tiên làm theo nhưng không được trái tài liệu):
{notes}

TÀI LIỆU:
{sources}
"""

# ── Battle-map plan ───────────────────────────────────────────────────────────

_BATTLE_SYSTEM = """\
Bạn là chuyên gia lịch sử quân sự Việt Nam, soạn lược đồ trận đánh động cho học sinh {grade_level}.
Bạn KHÔNG vẽ bản đồ và KHÔNG đưa ra toạ độ. Bạn chỉ lập kế hoạch: các phe, các điểm mốc địa lý cần
đánh dấu, các giai đoạn diễn biến, và trong mỗi giai đoạn có những ký hiệu nào, đặt ở điểm mốc nào,
di chuyển từ điểm mốc nào tới điểm mốc nào.

QUY TẮC BẮT BUỘC
1. Chỉ dùng thông tin có trong TÀI LIỆU. Không bịa quân số, tên tướng, hướng tiến quân.
   Tài liệu không nói rõ hướng di chuyển thì không vẽ mũi tên đó.
2. Mỗi giai đoạn ghi "sources" là id các đoạn tài liệu đã dùng.
3. Điểm mốc là địa danh hoặc vùng địa lý có thể nhận ra trên bản đồ (sông, cửa biển, thành, núi,
   bờ sông...). Đặt tên ngắn, "hint" mô tả vị trí tương đối (phía đông, thượng lưu, hai bên bờ...)
   để người soạn dễ tìm trên ảnh. Dùng càng ít điểm mốc càng tốt (3–8).
4. Chỉ dùng các loại ký hiệu được liệt kê. Hướng hành động (arrow, flank, march, retreat) phải có
   "from" và "to"; mọi loại khác dùng "at". Không dùng loại "step".
5. Diễn biến theo đúng trình tự thời gian; mỗi giai đoạn chỉ thêm những gì MỚI xảy ra.
6. Lời thuyết minh 2–3 câu, rõ ràng, đúng với các ký hiệu của giai đoạn đó.
7. Chỉ trả về JSON đúng mẫu dưới đây. 100% tiếng Việt.

MẪU JSON
{{"factions": [{{"id": "ta", "name": "...", "side": "ally|enemy|neutral"}}],
 "anchors": [{{"id": "cua-song", "name": "...", "hint": "...", "kind": "point|area"}}],
 "phases": [{{"title": "...", "narration": "...", "sources": ["..."], "camera": "auto|full",
   "symbols": [{{"type": "stakes", "faction": "ta", "at": "cua-song", "label": "..."}},
               {{"type": "arrow", "faction": "dich", "from": "bien", "to": "cua-song", "via": null, "label": "..."}}]}}]}}
"""

_BATTLE_USER = """\
TRẬN ĐÁNH: {context_name} ({year_label}) — {location}
MÔ TẢ: {context_description}

CÁC PHE ĐÃ CÓ (giữ nguyên id nếu dùng lại):
{factions}
CÁC ĐIỂM MỐC ĐÃ ĐẶT TRÊN ẢNH (ưu tiên dùng lại):
{anchors}

LOẠI KÝ HIỆU ĐƯỢC DÙNG:
- Hướng hành động (cần from, to): arrow = tiến công, flank = vu hồi/bao vây, march = cơ động, retreat = rút lui
- Tuyến (cần at): defenseLine = phòng tuyến, stakes = bãi cọc
- Lực lượng (cần at): infantry = bộ binh, cavalry = kỵ binh, archer = cung nỏ, artillery = pháo binh,
  armor = thiết giáp, navy = thuỷ quân, airforce = không quân
- Trận địa (cần at): headquarters = sở chỉ huy, fort = thành/đồn luỹ, camp = doanh trại, ambush = mai phục
- Diễn biến (cần at): clash = giao tranh, victory = chiến thắng, destroyed = bị tiêu diệt
- label (cần at): chữ chú thích hiện trên bản đồ
{era_rule}
YÊU CẦU
- Khoảng {phase_count} giai đoạn, theo góc nhìn: {perspective}.
- Giai đoạn đầu: bố trí lực lượng ban đầu. Giai đoạn cuối: kết quả trận đánh (victory / destroyed).
- Mỗi giai đoạn 1–6 ký hiệu; toàn bản đồ không quá 40 ký hiệu.

TÓM TẮT KỊCH BẢN KỂ CHUYỆN (nếu có, lời thuyết minh nên khớp):
{story_summary}

GHI CHÚ CỦA NGƯỜI SOẠN:
{notes}

TÀI LIỆU:
{sources}
"""

# ── Shared ────────────────────────────────────────────────────────────────────

_REPAIR = """\
JSON bạn vừa trả về có các lỗi sau:
{errors}

Hãy sửa đúng các lỗi trên, giữ nguyên các phần còn lại, và trả về toàn bộ JSON đã sửa theo đúng mẫu.
JSON cũ:
{old_json}
"""

_LOCATE = """\
Đây là một bản đồ lịch sử. Hãy tìm vị trí của các địa điểm sau trên ảnh.
Ưu tiên chữ địa danh in trên bản đồ; nếu không có chữ, dựa vào đặc điểm địa lý và gợi ý vị trí.
{anchors}

Trả về JSON: {{"anchors": [{{"id": "...", "found": true, "point": [x, y], "confidence": 0.7}}]}}
trong đó [x, y] là toạ độ theo thang 0–1000 (x từ trái sang phải, y từ trên xuống dưới).
Nếu không chắc chắn, đặt "found": false và bỏ "point". Không đoán bừa.
"""


def _lines(items: list[str], empty: str = "(không có)") -> str:
    return "\n".join(f"- {item}" for item in items) or empty


def build_story_prompts(
    mode: str,
    context: dict,
    narrator: dict | None,
    characters: list[dict],
    backgrounds: list[dict],
    options: dict,
    sources: str,
    previous_steps: list[dict] | None = None,
    step: dict | None = None,
    instruction: str | None = None,
    count: int | None = None,
) -> tuple[str, str]:
    if narrator:
        self_pronoun = narrator.get("selfPronoun") or "ta"
        address_pronoun = narrator.get("addressPronoun") or "ngươi"
        voice_rule = f'xưng "{self_pronoun}", gọi học sinh là "{address_pronoun}"'
    else:
        voice_rule = 'xưng "ta", gọi học sinh là "ngươi"'
    if options.get("tone"):
        voice_rule += f'; giọng điệu: {options["tone"]}'
    system = _STORY_SYSTEM.format(voice_rule=voice_rule, grade_level=options.get("gradeLevel") or "THCS")

    narrator_line = (
        f'{narrator["name"]} (id: {narrator["characterId"]}). {(narrator.get("background") or "")[:300]}'
        if narrator
        else 'Không có, dùng người dẫn truyện ("narrator").'
    )
    user = _STORY_CONTEXT.format(
        context_name=context.get("name", ""),
        year_label=context.get("yearLabel") or "không rõ năm",
        context_description=context.get("description") or "",
        narrator_line=narrator_line,
        characters=_lines([f'{c["characterId"]}: {c["name"]}, {(c.get("background") or "")[:150]}' for c in characters]),
        backgrounds=_lines([f'{b["id"]}: {b["name"]}' for b in backgrounds]),
    )

    if mode == "full":
        minutes = options.get("targetMinutes") or 3
        user += _STORY_FULL.format(
            target_minutes=minutes,
            line_count=minutes * 12,
            choice_count=options.get("choiceCount", 1),
            quiz_count=options.get("quizCount", 1),
        )
    elif mode == "continue":
        user += _STORY_CONTINUE.format(previous_steps=json.dumps(previous_steps or [], ensure_ascii=False), count=count)
    elif mode == "rewrite":
        user += _STORY_REWRITE.format(instruction=instruction, step=json.dumps(step, ensure_ascii=False))
    else:
        user += _STORY_QUIZ.format(previous_steps=json.dumps(previous_steps or [], ensure_ascii=False))

    user += _STORY_TAIL.format(notes=options.get("notes") or "(không có)", sources=sources)
    return system, user


def build_battle_prompts(
    context: dict,
    options: dict,
    existing_factions: list[dict],
    existing_anchors: list[dict],
    story_summary: str | None,
    era_rule: str,
    sources: str,
) -> tuple[str, str]:
    system = _BATTLE_SYSTEM.format(grade_level=options.get("gradeLevel") or "THCS")
    user = _BATTLE_USER.format(
        context_name=context.get("name", ""),
        year_label=context.get("yearLabel") or "không rõ năm",
        location=context.get("location") or "không rõ địa điểm",
        context_description=context.get("description") or "",
        factions=_lines([f'{f["id"]}: {f["name"]}' for f in existing_factions]),
        anchors=_lines([f'{a["id"]}: {a["name"]}' for a in existing_anchors]),
        era_rule=f"{era_rule}\n" if era_rule else "",
        phase_count=options.get("phaseCount") or 5,
        perspective=options.get("perspective") or "neutral",
        story_summary=story_summary or "(không có)",
        notes=options.get("notes") or "(không có)",
        sources=sources,
    )
    return system, user


def build_repair_prompt(errors: list[str], old_json: str) -> str:
    return _REPAIR.format(errors=_lines(errors), old_json=old_json)


def build_locate_prompt(anchors: list[dict]) -> str:
    return _LOCATE.format(
        anchors=_lines([f'{a["id"]}: {a["name"]}' + (f' — {a["hint"]}' if a.get("hint") else "") for a in anchors])
    )
