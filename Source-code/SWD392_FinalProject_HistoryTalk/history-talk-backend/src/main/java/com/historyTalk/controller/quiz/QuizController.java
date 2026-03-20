package com.historyTalk.controller.quiz;

import com.historyTalk.dto.ApiResponse;
import com.historyTalk.dto.quiz.*;
import com.historyTalk.dto.PaginatedResponse;
import com.historyTalk.service.quiz.QuizService;
import com.historyTalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/quizzes")
@Tag(name = "Quizzes (Customer)", description = "Endpoints for customers to take quizzes")
public class QuizController {

    private final QuizService quizService;

    @GetMapping
    @Operation(summary = "Get all available quizzes", description = "Retrieve all quizzes available for customers to take (no pagination)")
    public ResponseEntity<ApiResponse<List<QuizCustomerResponse>>> getAllQuizzes(
            @RequestParam(required = false) String search) {

        log.info("GET /api/v1/quizzes - search: {}", search);

        List<QuizCustomerResponse> data = quizService.getAllQuizzesForCustomer(search);

        return ResponseEntity.ok(ApiResponse.success(data, "Quizzes retrieved successfully"));
    }

    @GetMapping("/{quizId}")
    @Operation(summary = "Get quiz details", description = "Retrieve quiz details before starting (without correct answers)")
    public ResponseEntity<ApiResponse<QuizCustomerResponse>> getQuizById(
            @PathVariable String quizId) {

        log.info("GET /api/v1/quizzes/{} - get quiz details", quizId);

        QuizCustomerResponse data = quizService.getQuizByIdForCustomer(quizId);

        return ResponseEntity.ok(ApiResponse.success(data, "Quiz retrieved successfully"));
    }

    @PostMapping("/{quizId}/start")
    @PreAuthorize("hasRole('CUSTOMER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Start a quiz", description = "Initialize a quiz session and retrieve questions with options")
    public ResponseEntity<ApiResponse<QuizStartResponse>> startQuiz(
            @PathVariable String quizId) {

        log.info("POST /api/v1/quizzes/{}/start - start quiz", quizId);

        String userId = SecurityUtils.getUserId();
        QuizStartResponse data = quizService.startQuiz(quizId, userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Quiz started successfully"));
    }

    @PostMapping("/submit")
    @PreAuthorize("hasRole('CUSTOMER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Submit quiz answers", description = "Submit answers for a quiz session and get the score")
    public ResponseEntity<ApiResponse<QuizSubmitResponse>> submitQuiz(
            @Valid @RequestBody QuizSubmitRequest request) {

        log.info("POST /api/v1/quizzes/submit - submit quiz session");

        String userId = SecurityUtils.getUserId();
        QuizSubmitResponse data = quizService.submitQuiz(request, userId);

        return ResponseEntity.ok(ApiResponse.success(data, "Quiz submitted successfully"));
    }

    @GetMapping("/results/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get my quiz history", description = "Retrieve the current user's quiz history with pagination")
    public ResponseEntity<ApiResponse<PaginatedResponse<QuizHistoryResponse>>> getMyQuizHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("GET /api/v1/quizzes/results/me - get quiz history, page: {}, size: {}", page, size);

        String userId = SecurityUtils.getUserId();
        Pageable pageable = PageRequest.of(page, size);
        PaginatedResponse<QuizHistoryResponse> data = quizService.getQuizHistory(userId, pageable);

        return ResponseEntity.ok(ApiResponse.success(data, "Quiz history retrieved successfully"));
    }

    @PatchMapping("/results/{resultId}/soft-delete")
    @PreAuthorize("hasRole('CUSTOMER') or hasRole('STAFF') or hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Soft delete quiz result", description = "Soft delete a quiz result (owner or Admin only)")
    public ResponseEntity<ApiResponse<?>> softDeleteQuizResult(
            @PathVariable String resultId) {
        log.info("PATCH /api/v1/quizzes/results/{}/soft-delete", resultId);
        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        quizService.softDeleteQuizResult(resultId, userId, userRole);
        return ResponseEntity.ok(ApiResponse.success(null, "Quiz result soft-deleted successfully"));
    }

    @PatchMapping("/sessions/{sessionId}/soft-delete")
    @PreAuthorize("hasRole('CUSTOMER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Soft delete quiz session", description = "Soft delete a quiz session (owner only)")
    public ResponseEntity<ApiResponse<?>> softDeleteQuizSession(
            @PathVariable String sessionId) {
        log.info("PATCH /api/v1/quizzes/sessions/{}/soft-delete", sessionId);
        String userId = SecurityUtils.getUserId();
        quizService.softDeleteQuizSession(sessionId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Quiz session soft-deleted successfully"));
    }
}
