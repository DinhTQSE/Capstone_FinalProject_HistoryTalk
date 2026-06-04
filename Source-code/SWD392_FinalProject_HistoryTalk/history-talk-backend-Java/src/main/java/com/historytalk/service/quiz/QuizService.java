package com.historytalk.service.quiz;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.quiz.*;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface QuizService {

    // ==================== Customer ====================

    List<QuizCustomerResponse> getAllQuizzesForCustomer(String search, UUID userId);

    QuizCustomerResponse getQuizByIdForCustomer(String quizId, UUID userId);

    QuizStartResponse startQuiz(String quizId, UUID userId, Integer limitedTime);

    QuizSubmitResponse submitQuiz(QuizSubmitRequest request, UUID userId);

    PaginatedResponse<QuizHistoryResponse> getQuizHistory(UUID userId, Pageable pageable);

    // userId = null means admin mode (SYSTEM_ADMIN) — skip ownership check
    QuizSessionDetailResponse getSessionDetail(String sessionId, UUID userId);

    // ==================== Staff ====================

    PaginatedResponse<QuizStaffResponse> getAllQuizzesForStaff(String search, String era, Pageable pageable, String role);

    PaginatedResponse<QuizHistoryResponse> getAllUsersQuizHistory(Pageable pageable);

    PaginatedResponse<QuizHistoryResponse> getQuizHistoryByUserId(String userId, Pageable pageable);

    QuizStaffResponse getQuizByIdForStaff(String quizId);

    QuizStaffResponse createQuiz(CreateQuizRequest request, UUID userId);

    QuizStaffResponse updateQuiz(String quizId, UpdateQuizRequest request);

    void deleteQuiz(String quizId);

    void softDeleteQuiz(String quizId);

    QuestionResponse addQuestion(String quizId, QuestionRequest request);

    void updateQuestion(String quizId, String questionId, QuestionRequest request);

    void deleteQuestion(String quizId, String questionId);

    // ==================== Import ====================

    QuizImportResponse importQuizzesFromCsv(MultipartFile file, UUID userId);
}
