package com.historytalk.service.quiz.csv;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.quiz.quizdata.MatchingItem;
import com.historytalk.dto.quiz.quizdata.MatchingPair;
import com.historytalk.dto.quiz.quizdata.MatchingQuestionData;
import com.historytalk.entity.enums.QuestionType;
import com.historytalk.exception.InvalidRequestException;
import org.apache.commons.csv.CSVRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * MATCHING: each option cell holds one pair "Left => Right". Right items are shuffled
 * before their ids are assigned so neither order nor ids reveal the answer.
 */
@Component
public class MatchingCsvParser implements QuestionCsvParserStrategy {

    private static final String ARROW = "=>";

    private final ObjectMapper objectMapper;
    private final Random random;

    @Autowired
    public MatchingCsvParser(ObjectMapper objectMapper) {
        this(objectMapper, new Random());
    }

    MatchingCsvParser(ObjectMapper objectMapper, Random random) {
        this.objectMapper = objectMapper;
        this.random = random;
    }

    @Override
    public QuestionType supports() {
        return QuestionType.MATCHING;
    }

    @Override
    public JsonNode parseQuestionData(CSVRecord row) {
        QuizCsvColumns.requireBlank(row, QuizCsvColumns.CORRECT_ANSWER,
                "Câu hỏi MATCHING không dùng cột correctAnswer — mỗi ô option là một cặp 'Vế trái => Vế phải'");
        String notForType = " chỉ dùng cho FILL_IN_BLANK, hãy để trống với MATCHING";
        QuizCsvColumns.requireBlank(row, QuizCsvColumns.CASE_SENSITIVE, QuizCsvColumns.CASE_SENSITIVE + notForType);
        QuizCsvColumns.requireBlank(row, QuizCsvColumns.TRIM_WHITESPACE, QuizCsvColumns.TRIM_WHITESPACE + notForType);

        List<String> cells = QuizCsvColumns.filledOptions(row);
        if (cells.size() < 2) {
            throw new InvalidRequestException("Câu hỏi MATCHING cần ít nhất 2 cặp (option1, option2)");
        }

        List<String> lefts = new ArrayList<>();
        List<String> rights = new ArrayList<>();
        Set<String> seenLeft = new HashSet<>();
        Set<String> seenRight = new HashSet<>();
        for (int i = 0; i < cells.size(); i++) {
            String cell = cells.get(i);
            int arrow = cell.indexOf(ARROW);
            String left = arrow < 0 ? "" : cell.substring(0, arrow).trim();
            String right = arrow < 0 ? "" : cell.substring(arrow + ARROW.length()).trim();
            if (left.isEmpty() || right.isEmpty()) {
                throw new InvalidRequestException(QuizCsvColumns.option(i + 1)
                        + " phải có dạng 'Vế trái => Vế phải', nhận: '" + cell + "'");
            }
            if (!seenLeft.add(left)) {
                throw new InvalidRequestException("Trùng vế trái '" + left + "' trong các cặp MATCHING");
            }
            if (!seenRight.add(right)) {
                throw new InvalidRequestException("Trùng vế phải '" + right + "' trong các cặp MATCHING");
            }
            lefts.add(left);
            rights.add(right);
        }

        List<MatchingItem> leftItems = new ArrayList<>();
        for (int i = 0; i < lefts.size(); i++) {
            leftItems.add(MatchingItem.builder().id("L" + (i + 1)).text(lefts.get(i)).build());
        }

        List<String> shuffledRights = new ArrayList<>(rights);
        Collections.shuffle(shuffledRights, random);
        List<MatchingItem> rightItems = new ArrayList<>();
        Map<String, String> rightIdByText = new HashMap<>();
        for (int i = 0; i < shuffledRights.size(); i++) {
            String id = "R" + (i + 1);
            rightItems.add(MatchingItem.builder().id(id).text(shuffledRights.get(i)).build());
            rightIdByText.put(shuffledRights.get(i), id);
        }

        List<MatchingPair> correctPairs = new ArrayList<>();
        for (int i = 0; i < lefts.size(); i++) {
            correctPairs.add(MatchingPair.builder()
                    .leftId(leftItems.get(i).getId())
                    .rightId(rightIdByText.get(rights.get(i)))
                    .build());
        }

        return objectMapper.valueToTree(MatchingQuestionData.builder()
                .leftItems(leftItems)
                .rightItems(rightItems)
                .correctPairs(correctPairs)
                .build());
    }
}
