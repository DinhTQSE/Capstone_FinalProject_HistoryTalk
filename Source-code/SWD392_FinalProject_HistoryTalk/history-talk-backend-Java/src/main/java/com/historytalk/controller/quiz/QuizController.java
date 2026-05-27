package com.historytalk.controller.quiz;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.quiz.*;
import com.historytalk.service.quiz.QuizService;
import com.historytalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/quizzes")
@Tag(name = "Quizzes (Customer)", description = "Endpoints for customers to browse and take quizzes")
public class QuizController {

    private final QuizService quizService;

    /**
     * GET /quizzes
     * Public endpoint — returns array (not paginated).
     * playCount is per-user if authenticated, 0 if not.
     */
    @GetMapping
    @Operation(summary = "List active quizzes", description = "Returns all active quizzes. Optionally filter by search term.")
    public ResponseEntity<ApiResponse<List<QuizCustomerResponse>>> getAllQuizzes(
            @RequestParam(required = false) String search) {

        log.info("GET /api/v1/quizzes search={}", search);
        UUID userId = resolveUserId();
        List<QuizCustomerResponse> data = quizService.getAllQuizzesForCustomer(search, userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Quizzes retrieved successfully"));
    }

    /**
     * GET /quizzes/:quizId
     * Public endpoint — returns QuizSet shape.
     */
    @GetMapping("/{quizId}")
    @Operation(summary = "Get quiz details", description = "Retrieve quiz detail. playCount reflects this user's completions (0 if unauthenticated).")
    public ResponseEntity<ApiResponse<QuizCustomerResponse>> getQuizById(
            @PathVariable String quizId) {

        log.info("GET /api/v1/quizzes/{}", quizId);
        UUID userId = resolveUserId();
        QuizCustomerResponse data = quizService.getQuizByIdForCustomer(quizId, userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Quiz retrieved successfully"));
    }

    /**
     * POST /quizzes/:quizId/start
     * Requires CUSTOMER role. Optional ?limitedTime=N (seconds).
     */
    @PostMapping("/{quizId}/start")
    @PreAuthorize("hasRole('CUSTOMER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Start a quiz session", description = "Creates a session and returns questions. Pass ?limitedTime=N (seconds) to set a time limit.")
    public ResponseEntity<ApiResponse<QuizStartResponse>> startQuiz(
            @PathVariable String quizId,
            @RequestParam(required = false) Integer limitedTime) {

        log.info("POST /api/v1/quizzes/{}/start limitedTime={}", quizId, limitedTime);
        UUID userId = UUID.fromString(SecurityUtils.getUserId());
        QuizStartResponse data = quizService.startQuiz(quizId, userId, limitedTime);
        return ResponseEntity.ok(ApiResponse.success(data, "Quiz started successfully"));
    }

    /**
     * POST /quizzes/submit
     * Requires CUSTOMER role.
     */
    @PostMapping("/submit")
    @PreAuthorize("hasRole('CUSTOMER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Submit quiz answers", description = "Submit answers and receive score. Fails if time limit expired or already submitted.")
    public ResponseEntity<ApiResponse<QuizSubmitResponse>> submitQuiz(
            @Valid @RequestBody QuizSubmitRequest request) {

        log.info("POST /api/v1/quizzes/submit sessionId={}", request.getSessionId());
        UUID userId = UUID.fromString(SecurityUtils.getUserId());
        QuizSubmitResponse data = quizService.submitQuiz(request, userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Quiz submitted successfully"));
    }

    /**
     * GET /quizzes/results/me
     * Requires CUSTOMER role. Paginated, 0-indexed.
     */
    @GetMapping("/results/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Get my quiz history", description = "Paginated list of completed quiz sessions for the current user.")
    public ResponseEntity<ApiResponse<PaginatedResponse<QuizHistoryResponse>>> getMyQuizHistory(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("GET /api/v1/quizzes/results/me page={} size={}", page, size);
        UUID userId = UUID.fromString(SecurityUtils.getUserId());
        Pageable pageable = PageRequest.of(page, size);
        PaginatedResponse<QuizHistoryResponse> data = quizService.getQuizHistory(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Quiz history retrieved successfully"));
    }

    /**
     * Safely extract userId from SecurityContext.
     * Returns null if the request is unauthenticated (public endpoints).
     */
    private UUID resolveUserId() {
        try {
            String id = SecurityUtils.getUserId();
            return id != null ? UUID.fromString(id) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
