package com.historytalk.entity;

import com.historytalk.entity.document.Document;
import com.historytalk.entity.quiz.Question;
import com.historytalk.entity.quiz.Quiz;
import com.historytalk.entity.quiz.QuizAnswerDetail;
import com.historytalk.entity.quiz.QuizSession;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class EntityFlywayMappingContractTest {

    @Test
    void quizEntityNullabilityMatchesFlywayBaseline() throws NoSuchFieldException {
        assertNullableColumn(Quiz.class, "playCount");
        assertNullableColumn(Quiz.class, "rating");
        assertNullableColumn(Quiz.class, "createdDate");

        assertNullableColumn(Question.class, "options");
        assertNullableColumn(Question.class, "correctAnswer");
        assertNullableColumn(Question.class, "createdDate");

        assertNullableColumn(QuizSession.class, "startTime");
        assertNullableColumn(QuizSession.class, "isSubmitted");
        assertNullableColumn(QuizSession.class, "createdDate");

        assertNullableColumn(QuizAnswerDetail.class, "createdDate");
    }

    @Test
    void documentEntityDoesNotDeclareIndexesMissingFromFlywayBaseline() {
        Table table = Document.class.getAnnotation(Table.class);

        assertThat(table.indexes()).isEmpty();
    }

    private static void assertNullableColumn(Class<?> entityType, String fieldName)
            throws NoSuchFieldException {
        Field field = entityType.getDeclaredField(fieldName);
        Column column = field.getAnnotation(Column.class);

        assertThat(column.nullable())
                .as("%s.%s nullable", entityType.getSimpleName(), fieldName)
                .isTrue();
    }
}
