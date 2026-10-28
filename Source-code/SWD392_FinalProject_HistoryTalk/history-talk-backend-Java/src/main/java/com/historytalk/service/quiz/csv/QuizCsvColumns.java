package com.historytalk.service.quiz.csv;

import com.historytalk.exception.InvalidRequestException;
import org.apache.commons.csv.CSVRecord;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/** Column names and cell helpers shared by the quiz CSV parsers. */
final class QuizCsvColumns {

    static final String TITLE = "title";
    static final String CONTEXT_ID = "contextId";
    static final String LEVEL = "level";
    static final String QUESTION_TYPE = "questionType";
    static final String QUESTION_CONTENT = "questionContent";
    static final String CORRECT_ANSWER = "correctAnswer";
    static final String CASE_SENSITIVE = "caseSensitive";
    static final String TRIM_WHITESPACE = "trimWhitespace";
    static final String EXPLANATION = "explanation";
    static final int MAX_OPTIONS = 6;

    private static final Pattern MULTI_VALUE_SEPARATOR = Pattern.compile(Pattern.quote("|"));

    private QuizCsvColumns() {
    }

    /** Trimmed cell value; empty when the column is absent from the header or the row is short. */
    static String value(CSVRecord row, String column) {
        return row.isSet(column) ? row.get(column).trim() : "";
    }

    static String option(int number) {
        return "option" + number;
    }

    /** Non-blank option cells in order; a blank cell followed by a filled one is rejected. */
    static List<String> filledOptions(CSVRecord row) {
        List<String> filled = new ArrayList<>();
        int firstBlank = 0;
        for (int i = 1; i <= MAX_OPTIONS; i++) {
            String cell = value(row, option(i));
            if (cell.isEmpty()) {
                if (firstBlank == 0) {
                    firstBlank = i;
                }
            } else {
                if (firstBlank != 0) {
                    throw new InvalidRequestException(
                            option(firstBlank) + " không được để trống khi " + option(i) + " có giá trị");
                }
                filled.add(cell);
            }
        }
        return filled;
    }

    /** Splits a "a|b|c" cell, trimming each part and dropping empty parts. */
    static List<String> splitMultiValue(String cell) {
        return Arrays.stream(MULTI_VALUE_SEPARATOR.split(cell))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    static void requireBlank(CSVRecord row, String column, String reason) {
        if (!value(row, column).isEmpty()) {
            throw new InvalidRequestException(reason);
        }
    }
}
