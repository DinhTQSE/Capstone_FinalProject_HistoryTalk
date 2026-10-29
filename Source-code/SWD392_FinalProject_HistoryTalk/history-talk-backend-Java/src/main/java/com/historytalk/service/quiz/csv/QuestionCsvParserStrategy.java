package com.historytalk.service.quiz.csv;

import com.fasterxml.jackson.databind.JsonNode;
import com.historytalk.entity.enums.QuestionType;
import org.apache.commons.csv.CSVRecord;

/**
 * Builds the typed {@code questionData} payload for one question type from a quiz CSV row.
 * Implementations throw {@link com.historytalk.exception.InvalidRequestException} without a line
 * prefix; {@link QuizCsvRowParser} adds it.
 */
public interface QuestionCsvParserStrategy {

    QuestionType supports();

    JsonNode parseQuestionData(CSVRecord row);
}
