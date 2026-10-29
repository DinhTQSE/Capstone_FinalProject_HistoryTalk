package com.historytalk.service.quiz.csv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.quiz.QuestionRequest;
import com.historytalk.dto.quiz.quizdata.MatchingItem;
import com.historytalk.dto.quiz.quizdata.MatchingPair;
import com.historytalk.dto.quiz.quizdata.MatchingQuestionData;
import com.historytalk.dto.quiz.quizdata.MultipleChoiceQuestionData;
import com.historytalk.entity.enums.QuestionType;
import com.historytalk.exception.InvalidRequestException;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class QuizCsvRowParserTest {

    private static final List<String> COLUMNS = List.of(
            "title", "contextId", "level", "questionType", "questionContent",
            "option1", "option2", "option3", "option4", "option5", "option6",
            "correctAnswer", "caseSensitive", "trimWhitespace", "explanation");

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QuizCsvFileReader reader = new QuizCsvFileReader();
    private final QuizCsvRowParser parser = new QuizCsvRowParser(List.of(
            new MultipleChoiceCsvParser(objectMapper),
            new FillInBlankCsvParser(objectMapper),
            new MatchingCsvParser(objectMapper, new Random(42))));

    // ==================== MULTIPLE_CHOICE ====================

    @Test
    void legacyFourOptionRowParsesAsMultipleChoice() throws Exception {
        CSVRecord row = parseCsv("title,contextId,level,questionContent,option1,option2,option3,option4,correctAnswer,explanation\n"
                + "Quiz,ctx,EASY,Ai chỉ huy?,A,B,C,D,2,Giải thích\n");

        QuestionRequest request = parser.parse(row);

        assertThat(request.getQuestionType()).isEqualTo(QuestionType.MULTIPLE_CHOICE);
        assertThat(request.getContent()).isEqualTo("Ai chỉ huy?");
        assertThat(request.getExplanation()).isEqualTo("Giải thích");
        assertThat(request.getQuestionData()).isEqualTo(objectMapper.readTree(
                "{\"options\":[{\"id\":\"0\",\"text\":\"A\"},{\"id\":\"1\",\"text\":\"B\"},"
                        + "{\"id\":\"2\",\"text\":\"C\"},{\"id\":\"3\",\"text\":\"D\"}],\"correctOptionIds\":[\"2\"]}"));
    }

    @Test
    void mcMultipleCorrectAndSixOptions() throws Exception {
        QuestionRequest request = parser.parse(rec(Map.of(
                "option1", "A", "option2", "B", "option3", "C", "option4", "D", "option5", "E", "option6", "F",
                "correctAnswer", "0|5")));

        MultipleChoiceQuestionData data = objectMapper.treeToValue(request.getQuestionData(), MultipleChoiceQuestionData.class);
        assertThat(data.getOptions()).hasSize(6);
        assertThat(data.getCorrectOptionIds()).containsExactly("0", "5");
    }

    @Test
    void mcGapInOptionsFails() {
        assertInvalid(Map.of("option1", "A", "option3", "C", "correctAnswer", "0"),
                "Dòng 2: option2 không được để trống khi option3 có giá trị");
    }

    @Test
    void mcCorrectIndexOutOfRangeFails() {
        assertThatThrownBy(() -> parser.parse(rec(Map.of(
                "option1", "A", "option2", "B", "option3", "C", "correctAnswer", "3"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Dòng 2")
                .hasMessageContaining("0-2");
    }

    @Test
    void mcNonNumericCorrectAnswerFails() {
        assertThatThrownBy(() -> parser.parse(rec(Map.of(
                "option1", "A", "option2", "B", "correctAnswer", "A"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("correctAnswer");
    }

    @Test
    void mcSingleOptionFails() {
        assertThatThrownBy(() -> parser.parse(rec(Map.of("option1", "A", "correctAnswer", "0"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("ít nhất 2");
    }

    // ==================== FILL_IN_BLANK ====================

    @Test
    void fillParsesAcceptedAnswersWithDefaults() throws Exception {
        QuestionRequest request = parser.parse(rec(Map.of(
                "questionType", "FILL_IN_BLANK", "correctAnswer", "938| năm 938 ")));

        assertThat(request.getQuestionType()).isEqualTo(QuestionType.FILL_IN_BLANK);
        assertThat(request.getQuestionData()).isEqualTo(objectMapper.readTree(
                "{\"acceptedAnswers\":[\"938\",\"năm 938\"],\"caseSensitive\":false,\"trimWhitespace\":true}"));
    }

    @Test
    void fillHonoursCaseSensitiveColumn() {
        QuestionRequest request = parser.parse(rec(Map.of(
                "questionType", "FILL_IN_BLANK", "correctAnswer", "Ngô Quyền", "caseSensitive", "TRUE")));

        assertThat(request.getQuestionData().get("caseSensitive").asBoolean()).isTrue();
    }

    @Test
    void fillInvalidBooleanFails() {
        assertThatThrownBy(() -> parser.parse(rec(Map.of(
                "questionType", "FILL_IN_BLANK", "correctAnswer", "938", "caseSensitive", "maybe"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("caseSensitive");
    }

    @Test
    void fillWithOptionsFails() {
        assertThatThrownBy(() -> parser.parse(rec(Map.of(
                "questionType", "FILL_IN_BLANK", "correctAnswer", "938", "option1", "A"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("FILL_IN_BLANK")
                .hasMessageContaining("option");
    }

    @Test
    void fillWithoutAnswerFails() {
        assertThatThrownBy(() -> parser.parse(rec(Map.of("questionType", "FILL_IN_BLANK"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("correctAnswer");
    }

    // ==================== MATCHING ====================

    @Test
    void matchingParsesPairs() throws Exception {
        QuestionRequest request = parser.parse(rec(Map.of(
                "questionType", "MATCHING",
                "option1", "Ngô Quyền => Bạch Đằng 938",
                "option2", "Lê Lợi => Chi Lăng",
                "option3", "Quang Trung => Ngọc Hồi")));

        MatchingQuestionData data = objectMapper.treeToValue(request.getQuestionData(), MatchingQuestionData.class);
        assertThat(data.getLeftItems()).extracting(MatchingItem::getId).containsExactly("L1", "L2", "L3");
        assertThat(data.getLeftItems()).extracting(MatchingItem::getText)
                .containsExactly("Ngô Quyền", "Lê Lợi", "Quang Trung");
        Map<String, String> rightTextById = data.getRightItems().stream()
                .collect(Collectors.toMap(MatchingItem::getId, MatchingItem::getText));
        Map<String, String> matched = new LinkedHashMap<>();
        for (MatchingPair pair : data.getCorrectPairs()) {
            matched.put(pair.getLeftId(), rightTextById.get(pair.getRightId()));
        }
        assertThat(matched).containsExactly(
                Map.entry("L1", "Bạch Đằng 938"), Map.entry("L2", "Chi Lăng"), Map.entry("L3", "Ngọc Hồi"));
    }

    @Test
    void matchingShufflesRightItemsBeforeAssigningIds() throws Exception {
        QuestionRequest request = parser.parse(rec(Map.of(
                "questionType", "MATCHING",
                "option1", "a => 1", "option2", "b => 2", "option3", "c => 3", "option4", "d => 4")));

        MatchingQuestionData data = objectMapper.treeToValue(request.getQuestionData(), MatchingQuestionData.class);
        assertThat(data.getRightItems()).extracting(MatchingItem::getText).isNotEqualTo(List.of("1", "2", "3", "4"));
        assertThat(data.getRightItems()).extracting(MatchingItem::getId).containsExactly("R1", "R2", "R3", "R4");
    }

    @Test
    void matchingCellWithoutArrowFails() {
        assertThatThrownBy(() -> parser.parse(rec(Map.of(
                "questionType", "MATCHING", "option1", "a => 1", "option2", "b - 2"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("option2")
                .hasMessageContaining("=>");
    }

    @Test
    void matchingDuplicateRightTextFails() {
        assertThatThrownBy(() -> parser.parse(rec(Map.of(
                "questionType", "MATCHING", "option1", "a => 1", "option2", "b => 1"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Trùng");
    }

    @Test
    void matchingWithCorrectAnswerFails() {
        assertThatThrownBy(() -> parser.parse(rec(Map.of(
                "questionType", "MATCHING", "option1", "a => 1", "option2", "b => 2", "correctAnswer", "0"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("correctAnswer");
    }

    // ==================== Dispatcher ====================

    @Test
    void unknownQuestionTypeFails() {
        assertInvalid(Map.of("questionType", "ESSAY"),
                "Dòng 2: questionType không hợp lệ: 'ESSAY'. Giá trị hợp lệ: MULTIPLE_CHOICE, FILL_IN_BLANK, MATCHING");
    }

    @Test
    void lowercaseQuestionTypeAccepted() {
        QuestionRequest request = parser.parse(rec(Map.of(
                "questionType", "matching", "option1", "a => 1", "option2", "b => 2")));

        assertThat(request.getQuestionType()).isEqualTo(QuestionType.MATCHING);
    }

    @Test
    void blankQuestionContentFails() {
        assertThatThrownBy(() -> parser.parse(rec(Map.of(
                "questionContent", "", "option1", "A", "option2", "B", "correctAnswer", "0"))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("questionContent");
    }

    @Test
    void blankExplanationBecomesNull() {
        QuestionRequest request = parser.parse(rec(Map.of("option1", "A", "option2", "B", "correctAnswer", "0")));

        assertThat(request.getExplanation()).isNull();
    }

    // ==================== Helpers ====================

    private void assertInvalid(Map<String, String> overrides, String message) {
        assertThatThrownBy(() -> parser.parse(rec(overrides)))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessage(message);
    }

    /** One data row under the full header; unspecified columns get a sensible default or stay empty. */
    private CSVRecord rec(Map<String, String> overrides) {
        Map<String, String> defaults = Map.of(
                "title", "Quiz", "contextId", "ctx", "level", "EASY", "questionContent", "Câu hỏi");
        String values = COLUMNS.stream()
                .map(c -> overrides.getOrDefault(c, defaults.getOrDefault(c, "")))
                .collect(Collectors.joining(","));
        return parseCsv(String.join(",", COLUMNS) + "\n" + values + "\n");
    }

    private CSVRecord parseCsv(String csv) {
        return reader.read(new MockMultipartFile("file", "q.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)))
                .get(0);
    }
}
