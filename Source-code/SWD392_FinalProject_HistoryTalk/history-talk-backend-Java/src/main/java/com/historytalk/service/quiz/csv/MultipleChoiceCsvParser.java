package com.historytalk.service.quiz.csv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.quiz.quizdata.MultipleChoiceQuestionData;
import com.historytalk.dto.quiz.quizdata.QuestionOption;
import com.historytalk.entity.enums.QuestionType;
import com.historytalk.exception.InvalidRequestException;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * MULTIPLE_CHOICE: option1..option6 hold the choices, correctAnswer holds 0-based
 * indices separated by '|' (e.g. "0|2").
 */
@Component
public class MultipleChoiceCsvParser implements QuestionCsvParserStrategy {

    private final ObjectMapper objectMapper;

    public MultipleChoiceCsvParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public QuestionType supports() {
        return QuestionType.MULTIPLE_CHOICE;
    }

    @Override
    public JsonNode parseQuestionData(CSVRecord row) {
        String notForType = " chỉ dùng cho FILL_IN_BLANK, hãy để trống với MULTIPLE_CHOICE";
        QuizCsvColumns.requireBlank(row, QuizCsvColumns.CASE_SENSITIVE, QuizCsvColumns.CASE_SENSITIVE + notForType);
        QuizCsvColumns.requireBlank(row, QuizCsvColumns.TRIM_WHITESPACE, QuizCsvColumns.TRIM_WHITESPACE + notForType);

        List<String> texts = QuizCsvColumns.filledOptions(row);
        if (texts.size() < 2) {
            throw new InvalidRequestException("Câu hỏi MULTIPLE_CHOICE cần ít nhất 2 lựa chọn (option1, option2)");
        }
        List<QuestionOption> options = new ArrayList<>();
        for (int i = 0; i < texts.size(); i++) {
            options.add(QuestionOption.builder().id(String.valueOf(i)).text(texts.get(i)).build());
        }

        int maxIndex = texts.size() - 1;
        List<String> rawCorrect = QuizCsvColumns.splitMultiValue(QuizCsvColumns.value(row, QuizCsvColumns.CORRECT_ANSWER));
        if (rawCorrect.isEmpty()) {
            throw new InvalidRequestException("correctAnswer không được để trống với MULTIPLE_CHOICE");
        }
        List<String> correctIds = new ArrayList<>();
        for (String raw : rawCorrect) {
            int index;
            try {
                index = Integer.parseInt(raw);
            } catch (NumberFormatException e) {
                throw new InvalidRequestException("correctAnswer phải là số thứ tự lựa chọn (0-" + maxIndex
                        + "), nhiều đáp án cách nhau bởi '|', nhận: '" + raw + "'");
            }
            if (index < 0 || index > maxIndex) {
                throw new InvalidRequestException("correctAnswer phải trong khoảng 0-" + maxIndex + ", nhận: " + index);
            }
            String id = String.valueOf(index);
            if (correctIds.contains(id)) {
                throw new InvalidRequestException("Trùng đáp án đúng trong correctAnswer: " + index);
            }
            correctIds.add(id);
        }

        return objectMapper.valueToTree(MultipleChoiceQuestionData.builder()
                .options(options)
                .correctOptionIds(correctIds)
                .build());
    }
}
