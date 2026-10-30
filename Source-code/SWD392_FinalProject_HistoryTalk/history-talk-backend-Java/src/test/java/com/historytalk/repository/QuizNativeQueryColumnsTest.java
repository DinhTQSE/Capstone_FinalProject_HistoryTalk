package com.historytalk.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.Query;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repositories are mocked in service tests, so native SQL against dropped columns only fails at runtime.
 * This guards the columns removed by V20261031100000 / V20261031100100.
 */
class QuizNativeQueryColumnsTest {

    private static final List<Class<?>> QUIZ_REPOSITORIES = List.of(
            QuizRepository.class, QuizSessionRepository.class, QuizAnswerDetailRepository.class,
            QuestionRepository.class, QuizQuestionRepository.class);

    // question.quiz_id: a subquery grouping the question table by quiz_id, or an explicit question.quiz_id
    private static final Pattern QUESTION_QUIZ_ID = Pattern.compile(
            "(?is)FROM\\s+question\\s+WHERE[^()]*GROUP\\s+BY\\s+quiz_id|\\bquestion\\.quiz_id\\b");
    // quiz.level
    private static final Pattern QUIZ_LEVEL = Pattern.compile("(?i)\\bq\\.level\\b");

    @Test
    void nativeQueriesDoNotReadDroppedColumns() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> repository : QUIZ_REPOSITORIES) {
            for (Method method : repository.getDeclaredMethods()) {
                Query query = method.getAnnotation(Query.class);
                if (query == null || !query.nativeQuery()) {
                    continue;
                }
                if (QUESTION_QUIZ_ID.matcher(query.value()).find() || QUIZ_LEVEL.matcher(query.value()).find()) {
                    offenders.add(repository.getSimpleName() + "." + method.getName());
                }
            }
        }

        assertThat(offenders).isEmpty();
    }
}
