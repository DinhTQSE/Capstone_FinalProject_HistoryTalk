package com.historytalk.service.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.entity.enums.QuestionType;
import com.historytalk.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuestionDataValidatorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QuestionDataValidator validator = new QuestionDataValidator(objectMapper);

    @Test
    void validMultipleChoicePasses() {
        assertThatCode(() -> validator.validate(QuestionType.MULTIPLE_CHOICE, json("""
                {"options":[{"id":"0","text":"A"},{"id":"1","text":"B"}],"correctOptionIds":["1"]}
                """))).doesNotThrowAnyException();
    }

    @Test
    void validFillInBlankPasses() {
        assertThatCode(() -> validator.validate(QuestionType.FILL_IN_BLANK, json("""
                {"acceptedAnswers":["938","năm 938"],"caseSensitive":false,"trimWhitespace":true}
                """))).doesNotThrowAnyException();
    }

    @Test
    void validMatchingPasses() {
        assertThatCode(() -> validator.validate(QuestionType.MATCHING, json("""
                {"leftItems":[{"id":"L1","text":"Ngô Quyền"},{"id":"L2","text":"Lê Lợi"}],
                 "rightItems":[{"id":"R1","text":"Chi Lăng"},{"id":"R2","text":"Bạch Đằng"}],
                 "correctPairs":[{"leftId":"L1","rightId":"R2"},{"leftId":"L2","rightId":"R1"}]}
                """))).doesNotThrowAnyException();
    }

    @Test
    void mcCorrectIdNotInOptionsFails() {
        assertInvalid(QuestionType.MULTIPLE_CHOICE, """
                {"options":[{"id":"0","text":"A"},{"id":"1","text":"B"}],"correctOptionIds":["9"]}
                """, "9");
    }

    @Test
    void mcSingleOptionFails() {
        assertInvalid(QuestionType.MULTIPLE_CHOICE, """
                {"options":[{"id":"0","text":"A"}],"correctOptionIds":["0"]}
                """, "options");
    }

    @Test
    void mcDuplicateOptionIdFails() {
        assertInvalid(QuestionType.MULTIPLE_CHOICE, """
                {"options":[{"id":"0","text":"A"},{"id":"0","text":"B"}],"correctOptionIds":["0"]}
                """, "0");
    }

    @Test
    void fillBlankAcceptedAnswerFails() {
        assertInvalid(QuestionType.FILL_IN_BLANK, """
                {"acceptedAnswers":["938",""],"caseSensitive":false,"trimWhitespace":true}
                """, "acceptedAnswers");
    }

    @Test
    void fillEmptyListFails() {
        assertInvalid(QuestionType.FILL_IN_BLANK, """
                {"acceptedAnswers":[],"caseSensitive":false,"trimWhitespace":true}
                """, "acceptedAnswers");
    }

    @Test
    void matchingPairCountMismatchFails() {
        assertInvalid(QuestionType.MATCHING, """
                {"leftItems":[{"id":"L1","text":"a"},{"id":"L2","text":"b"}],
                 "rightItems":[{"id":"R1","text":"c"},{"id":"R2","text":"d"}],
                 "correctPairs":[{"leftId":"L1","rightId":"R1"}]}
                """, "correctPairs");
    }

    @Test
    void matchingRightIdUsedTwiceFails() {
        assertInvalid(QuestionType.MATCHING, """
                {"leftItems":[{"id":"L1","text":"a"},{"id":"L2","text":"b"}],
                 "rightItems":[{"id":"R1","text":"c"},{"id":"R2","text":"d"}],
                 "correctPairs":[{"leftId":"L1","rightId":"R1"},{"leftId":"L2","rightId":"R1"}]}
                """, "R1");
    }

    @Test
    void matchingUnknownLeftIdFails() {
        assertInvalid(QuestionType.MATCHING, """
                {"leftItems":[{"id":"L1","text":"a"},{"id":"L2","text":"b"}],
                 "rightItems":[{"id":"R1","text":"c"},{"id":"R2","text":"d"}],
                 "correctPairs":[{"leftId":"L1","rightId":"R1"},{"leftId":"L9","rightId":"R2"}]}
                """, "L9");
    }

    @Test
    void nullDataFails() {
        assertThatThrownBy(() -> validator.validate(QuestionType.FILL_IN_BLANK, null))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage("Yêu cầu questionData cho câu hỏi loại FILL_IN_BLANK");
    }

    private void assertInvalid(QuestionType type, String rawJson, String messagePart) {
        assertThatThrownBy(() -> validator.validate(type, json(rawJson)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining(messagePart);
    }

    private JsonNode json(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
