package com.historytalk.service.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.quiz.quizdata.FillInBlankQuestionData;
import com.historytalk.dto.quiz.quizdata.MatchingItem;
import com.historytalk.dto.quiz.quizdata.MatchingPair;
import com.historytalk.dto.quiz.quizdata.MatchingQuestionData;
import com.historytalk.dto.quiz.quizdata.MultipleChoiceQuestionData;
import com.historytalk.dto.quiz.quizdata.QuestionOption;
import com.historytalk.entity.enums.QuestionType;
import com.historytalk.exception.InvalidRequestException;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Validates the typed {@code questionData} payload against its {@link QuestionType}
 * so that every row persisted to {@code question.question_data} can be graded
 * and sanitized without falling into an error branch.
 */
@Component
public class QuestionDataValidator {

    private final ObjectMapper objectMapper;

    public QuestionDataValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void validate(QuestionType type, JsonNode questionData) {
        if (questionData == null || questionData.isNull()) {
            throw new InvalidRequestException("Yêu cầu questionData cho câu hỏi loại " + type.name());
        }
        switch (type) {
            case MULTIPLE_CHOICE -> validateMultipleChoice(read(questionData, MultipleChoiceQuestionData.class, type));
            case FILL_IN_BLANK -> validateFillInBlank(read(questionData, FillInBlankQuestionData.class, type));
            case MATCHING -> validateMatching(read(questionData, MatchingQuestionData.class, type));
        }
    }

    private void validateMultipleChoice(MultipleChoiceQuestionData data) {
        List<QuestionOption> options = data.getOptions();
        if (options == null || options.size() < 2) {
            throw new InvalidRequestException("Câu hỏi MULTIPLE_CHOICE cần ít nhất 2 lựa chọn (options)");
        }
        Set<String> optionIds = new HashSet<>();
        for (QuestionOption option : options) {
            if (option == null || isBlank(option.getId()) || isBlank(option.getText())) {
                throw new InvalidRequestException("Mỗi lựa chọn (options) phải có id và text không rỗng");
            }
            if (!optionIds.add(option.getId())) {
                throw new InvalidRequestException("Trùng id lựa chọn: '" + option.getId() + "'");
            }
        }
        List<String> correctIds = data.getCorrectOptionIds();
        if (correctIds == null || correctIds.isEmpty()) {
            throw new InvalidRequestException("Câu hỏi MULTIPLE_CHOICE cần ít nhất 1 đáp án đúng (correctOptionIds)");
        }
        Set<String> seenCorrect = new HashSet<>();
        for (String correctId : correctIds) {
            if (!optionIds.contains(correctId)) {
                throw new InvalidRequestException("Đáp án đúng '" + correctId + "' không có trong danh sách lựa chọn");
            }
            if (!seenCorrect.add(correctId)) {
                throw new InvalidRequestException("Trùng đáp án đúng: '" + correctId + "'");
            }
        }
    }

    private void validateFillInBlank(FillInBlankQuestionData data) {
        List<String> accepted = data.getAcceptedAnswers();
        if (accepted == null || accepted.isEmpty()) {
            throw new InvalidRequestException("Câu hỏi FILL_IN_BLANK cần ít nhất 1 đáp án chấp nhận (acceptedAnswers)");
        }
        for (String answer : accepted) {
            if (isBlank(answer)) {
                throw new InvalidRequestException("acceptedAnswers không được chứa đáp án rỗng");
            }
        }
    }

    private void validateMatching(MatchingQuestionData data) {
        Set<String> leftIds = collectItemIds(data.getLeftItems(), "leftItems");
        Set<String> rightIds = collectItemIds(data.getRightItems(), "rightItems");
        List<MatchingPair> pairs = data.getCorrectPairs();
        if (pairs == null || pairs.size() != leftIds.size()) {
            throw new InvalidRequestException("Số cặp đúng (correctPairs) phải bằng số vế trái (leftItems)");
        }
        Set<String> usedLeft = new HashSet<>();
        Set<String> usedRight = new HashSet<>();
        for (MatchingPair pair : pairs) {
            if (pair == null || !leftIds.contains(pair.getLeftId())) {
                throw new InvalidRequestException("correctPairs tham chiếu leftId không tồn tại: '"
                        + (pair != null ? pair.getLeftId() : null) + "'");
            }
            if (!rightIds.contains(pair.getRightId())) {
                throw new InvalidRequestException("correctPairs tham chiếu rightId không tồn tại: '" + pair.getRightId() + "'");
            }
            if (!usedLeft.add(pair.getLeftId())) {
                throw new InvalidRequestException("leftId '" + pair.getLeftId() + "' được nối nhiều lần");
            }
            if (!usedRight.add(pair.getRightId())) {
                throw new InvalidRequestException("rightId '" + pair.getRightId() + "' được nối nhiều lần");
            }
        }
    }

    private Set<String> collectItemIds(List<MatchingItem> items, String field) {
        if (items == null || items.size() < 2) {
            throw new InvalidRequestException("Câu hỏi MATCHING cần ít nhất 2 phần tử trong " + field);
        }
        Set<String> ids = new HashSet<>();
        for (MatchingItem item : items) {
            if (item == null || isBlank(item.getId()) || isBlank(item.getText())) {
                throw new InvalidRequestException("Mỗi phần tử trong " + field + " phải có id và text không rỗng");
            }
            if (!ids.add(item.getId())) {
                throw new InvalidRequestException("Trùng id trong " + field + ": '" + item.getId() + "'");
            }
        }
        return ids;
    }

    private <T> T read(JsonNode node, Class<T> target, QuestionType type) {
        try {
            return objectMapper.treeToValue(node, target);
        } catch (Exception e) {
            throw new InvalidRequestException("questionData không đúng định dạng cho loại " + type.name());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
