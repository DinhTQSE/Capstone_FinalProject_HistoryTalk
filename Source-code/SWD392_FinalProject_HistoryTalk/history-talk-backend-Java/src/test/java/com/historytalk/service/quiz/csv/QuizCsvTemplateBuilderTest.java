package com.historytalk.service.quiz.csv;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.quiz.QuestionRequest;
import com.historytalk.entity.enums.QuestionDifficulty;
import com.historytalk.entity.enums.QuestionType;
import com.historytalk.service.quiz.QuestionDataValidator;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class QuizCsvTemplateBuilderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final QuizCsvTemplateBuilder builder = new QuizCsvTemplateBuilder();

    @Test
    void templateStartsWithUtf8Bom() {
        byte[] bytes = builder.build();

        assertThat(bytes[0] & 0xFF).isEqualTo(0xEF);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xBB);
        assertThat(bytes[2] & 0xFF).isEqualTo(0xBF);
    }

    @Test
    void templateContainsVietnameseIntact() {
        String content = new String(builder.build(), StandardCharsets.UTF_8);

        assertThat(content).contains("Trận Bạch Đằng");
    }

    @Test
    void templateRoundTripsThroughReaderAndParser() {
        QuizCsvRowParser parser = new QuizCsvRowParser(List.of(
                new MultipleChoiceCsvParser(objectMapper),
                new FillInBlankCsvParser(objectMapper),
                new MatchingCsvParser(objectMapper, new Random(7))));
        QuestionDataValidator validator = new QuestionDataValidator(objectMapper);

        List<CSVRecord> records = new QuizCsvFileReader().read(
                new MockMultipartFile("file", "quiz-import-template.csv", "text/csv", builder.build()));
        List<QuestionRequest> requests = records.stream().map(parser::parse).toList();

        assertThat(requests).extracting(QuestionRequest::getQuestionType).containsExactly(
                QuestionType.MULTIPLE_CHOICE, QuestionType.FILL_IN_BLANK, QuestionType.MATCHING);
        for (QuestionRequest request : requests) {
            assertThatCode(() -> validator.validate(request.getQuestionType(), request.getQuestionData()))
                    .doesNotThrowAnyException();
        }
        assertThat(records).extracting(r -> r.get("title")).containsOnly(records.get(0).get("title"));
        // One quiz whose questions each carry their own difficulty
        assertThat(requests).extracting(QuestionRequest::getDifficulty).containsExactly(
                QuestionDifficulty.EASY, QuestionDifficulty.MEDIUM, QuestionDifficulty.HARD);
    }
}
