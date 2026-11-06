import os
import sys
from pathlib import Path

os.environ.setdefault("SUPABASE_URL", "http://localhost:54321")
os.environ.setdefault("SUPABASE_KEY", "test-key")
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from history_talk_ai.application.prompting import prompt_builder  # noqa: E402
from history_talk_ai.dataaccess.java_backend.character_schema import CharacterData  # noqa: E402


def _character(**fields):
    return CharacterData(characterId="char-1", name="Văn Tiến Dũng", background="Đại tướng {chú thích}", **fields)


def test_characters_say_ta_and_nguoi_by_default():
    prompt = prompt_builder.build_chat_system_prompt(_character(), None)

    assert 'xưng "ta"' in prompt
    assert 'người đối diện là "ngươi"' in prompt


def test_configured_pronouns_are_used_everywhere():
    character = _character(selfPronoun="tôi", addressPronoun="bạn")

    assert 'xưng "tôi"' in prompt_builder.build_chat_system_prompt(character, None)
    assert 'Xưng "tôi", gọi người học là "bạn"' in prompt_builder.build_exam_question_prompt(character, None, "Tư liệu", [])
    assert 'Xưng "tôi", gọi người học là "bạn"' in prompt_builder.build_exam_grading_prompt(character, "Hỏi", "Đáp", "Tư liệu", "Trả lời")


def test_blank_pronouns_fall_back_to_the_defaults():
    assert prompt_builder.character_pronouns(_character(selfPronoun="  ", addressPronoun="")) == ("ta", "ngươi")


def test_era_rule_follows_the_lifespan_instead_of_assuming_ancient_times():
    prompt = prompt_builder.build_chat_system_prompt(_character(lifespan="1917 - 2002"), None)

    assert "1917 - 2002" in prompt
    assert "thời xưa" not in prompt
    assert "điện thoại" not in prompt


def test_braces_in_the_background_are_kept_as_written():
    prompt = prompt_builder.build_chat_system_prompt(_character(), None)

    assert "Đại tướng {chú thích}" in prompt
    assert "{{" not in prompt


def test_grading_is_encouraging_and_accepts_extra_correct_details():
    prompt = prompt_builder.build_exam_grading_prompt(_character(), "Hỏi", "Đáp", "Tư liệu", "Trả lời")

    assert "Nói thêm ý đúng khác" in prompt
    assert "KHÔNG chê bai hay xúc phạm" in prompt


def test_exam_questions_are_factual_and_in_the_first_person():
    prompt = prompt_builder.build_exam_question_prompt(_character(), None, "Tư liệu", [])

    assert "không hỏi ý kiến hay cảm nghĩ" in prompt
    assert "KHÔNG gọi tên mình là Văn Tiến Dũng" in prompt
