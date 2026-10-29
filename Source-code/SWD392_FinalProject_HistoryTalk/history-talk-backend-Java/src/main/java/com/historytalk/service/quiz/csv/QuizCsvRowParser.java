package com.historytalk.service.quiz.csv;

import com.historytalk.dto.quiz.QuestionRequest;
import com.historytalk.entity.enums.QuestionType;
import com.historytalk.exception.InvalidRequestException;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Turns one quiz CSV row into a {@link QuestionRequest}, picking the
 * {@link QuestionCsvParserStrategy} that matches the row's questionType.
 */
@Component
public class QuizCsvRowParser {

    private static final String VALID_TYPES = Arrays.stream(QuestionType.values())
            .map(Enum::name)
            .collect(Collectors.joining(", "));

    private final Map<QuestionType, QuestionCsvParserStrategy> strategies = new EnumMap<>(QuestionType.class);

    public QuizCsvRowParser(List<QuestionCsvParserStrategy> strategies) {
        for (QuestionCsvParserStrategy strategy : strategies) {
            this.strategies.put(strategy.supports(), strategy);
        }
    }

    public QuestionRequest parse(CSVRecord row) {
        try {
            QuestionType type = parseType(QuizCsvColumns.value(row, QuizCsvColumns.QUESTION_TYPE));
            String content = QuizCsvColumns.value(row, QuizCsvColumns.QUESTION_CONTENT);
            if (content.isEmpty()) {
                throw new InvalidRequestException("questionContent không được để trống");
            }
            QuestionCsvParserStrategy strategy = strategies.get(type);
            if (strategy == null) {
                throw new InvalidRequestException("Chưa hỗ trợ import CSV cho loại câu hỏi " + type.name());
            }
            String explanation = QuizCsvColumns.value(row, QuizCsvColumns.EXPLANATION);
            return QuestionRequest.builder()
                    .content(content)
                    .explanation(explanation.isEmpty() ? null : explanation)
                    .questionType(type)
                    .questionData(strategy.parseQuestionData(row))
                    .build();
        } catch (InvalidRequestException e) {
            throw new InvalidRequestException(QuizCsvFileReader.lineLabel(row) + ": " + e.getMessage());
        }
    }

    private static QuestionType parseType(String raw) {
        if (raw.isEmpty()) {
            return QuestionType.MULTIPLE_CHOICE;
        }
        try {
            return QuestionType.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("questionType không hợp lệ: '" + raw + "'. Giá trị hợp lệ: " + VALID_TYPES);
        }
    }
}
