package com.historyTalk.service.quiz;

import com.historyTalk.dto.quiz.*;
import com.historyTalk.dto.PaginatedResponse;
import com.historyTalk.entity.enums.EventEra;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface QuizService {

    // Staff operations
    PaginatedResponse<QuizStaffResponse> getAllQuizzesForStaff(String search, Integer grade, EventEra era, Pageable pageable);

    List<QuizCustomerResponse> getAllQuizzesForCustomer(String search);

    QuizStaffResponse getQuizByIdForStaff(String quizId);

    QuizCustomerResponse getQuizByIdForCustomer(String quizId);

    QuizStaffResponse createQuiz(CreateQuizRequest request, String userId);

    QuizStaffResponse updateQuiz(String quizId, UpdateQuizRequest request, String userId, String userRole);

    void deleteQuiz(String quizId, String userId, String userRole);

    void addQuestion(String quizId, QuestionRequest request, String userId, String userRole);

    void updateQuestion(String quizId, String questionId, QuestionRequest request, String userId, String userRole);

    void deleteQuestion(String quizId, String questionId, String userId, String userRole);

    void reorderQuestions(String quizId, List<String> questionIds, String userId, String userRole);

    PaginatedResponse<QuizStaffResponse> getQuizzesByContextForStaff(String contextId, String search, Integer grade, EventEra era, Pageable pageable);

    // Customer operations
    QuizStartResponse startQuiz(String quizId, String userId);

    QuizSubmitResponse submitQuiz(QuizSubmitRequest request, String userId);

    PaginatedResponse<QuizHistoryResponse> getQuizHistory(String userId, Pageable pageable);

    void softDeleteQuizResult(String resultId, String userId, String userRole);

    void softDeleteQuizSession(String sessionId, String userId);

}
