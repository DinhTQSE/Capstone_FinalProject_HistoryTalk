package com.historytalk.service.quiz.csv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.quiz.quizdata.FillInBlankQuestionData;
import com.historytalk.entity.enums.QuestionType;
import com.historytalk.exception.InvalidRequestException;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * FILL_IN_BLANK: correctAnswer holds the accepted answers separated by '|';
 * caseSensitive defaults to false and trimWhitespace to true.
 */
@Component
public class FillInBlankCsvParser implements QuestionCsvParserStrategy {

    private final ObjectMapper objectMapper;

    public FillInBlankCsvParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public QuestionType supports() {
        return QuestionType.FILL_IN_BLANK;
    }

    @Override
    public JsonNode parseQuestionData(CSVRecord row) {
        for (int i = 1; i <= QuizCsvColumns.MAX_OPTIONS; i++) {
            QuizCsvColumns.requireBlank(row, QuizCsvColumns.option(i),
                    "Câu hỏi FILL_IN_BLANK không dùng các cột option1..option6, hãy để trống " + QuizCsvColumns.option(i));
        }

        List<String> accepted = QuizCsvColumns.splitMultiValue(QuizCsvColumns.value(row, QuizCsvColumns.CORRECT_ANSWER));
        if (accepted.isEmpty()) {
            throw new InvalidRequestException("correctAnswer phải chứa ít nhất 1 đáp án cho FILL_IN_BLANK");
        }

        return objectMapper.valueToTree(FillInBlankQuestionData.builder()
                .acceptedAnswers(accepted)
                .caseSensitive(parseBoolean(row, QuizCsvColumns.CASE_SENSITIVE, false))
                .trimWhitespace(parseBoolean(row, QuizCsvColumns.TRIM_WHITESPACE, true))
                .build());
    }

    private static boolean parseBoolean(CSVRecord row, String column, boolean defaultValue) {
        String raw = QuizCsvColumns.value(row, column);
        if (raw.isEmpty()) {
            return defaultValue;
        }
        if (raw.equalsIgnoreCase("true")) {
            return true;
        }
        if (raw.equalsIgnoreCase("false")) {
            return false;
        }
        throw new InvalidRequestException(column + " phải là true hoặc false, nhận: '" + raw + "'");
    }
}
