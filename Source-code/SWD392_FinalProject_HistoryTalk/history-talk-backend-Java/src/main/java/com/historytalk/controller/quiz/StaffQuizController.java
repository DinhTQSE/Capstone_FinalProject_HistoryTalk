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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/staff/quizzes")
@PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Quizzes (Staff)", description = "Endpoints for content admin and system admin to manage quizzes")
public class StaffQuizController {

    private final QuizService quizService;

    /**
     * GET /staff/quizzes
     * Paginated list. Optional: search, era, page (0-indexed), size.
     */
    @GetMapping
    @Operation(summary = "List all quizzes (paginated)", description = "Retrieve quizzes with optional search and era filter.")
    public ResponseEntity<ApiResponse<PaginatedResponse<QuizStaffResponse>>> getAllQuizzes(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String era,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("GET /api/v1/staff/quizzes search={} era={} page={} size={}", search, era, page, size);
        Pageable pageable = PageRequest.of(page, size);
        String role = SecurityUtils.getRoleName();
        PaginatedResponse<QuizStaffResponse> data = quizService.getAllQuizzesForStaff(search, era, pageable, role);
        return ResponseEntity.ok(ApiResponse.success(data, "Quizzes retrieved successfully"));
    }

    /**
     * GET /staff/quizzes/:quizId
     * Full quiz detail including all questions.
     */
    @GetMapping("/{quizId}")
    @Operation(summary = "Get quiz by ID", description = "Retrieve full quiz detail including all questions.")
    public ResponseEntity<ApiResponse<QuizStaffResponse>> getQuizById(
            @PathVariable String quizId) {

        log.info("GET /api/v1/staff/quizzes/{}", quizId);
        QuizStaffResponse data = quizService.getQuizByIdForStaff(quizId);
        return ResponseEntity.ok(ApiResponse.success(data, "Quiz retrieved successfully"));
    }

    /**
     * POST /staff/quizzes
     * Create a new quiz with initial questions.
     */
    @PostMapping
    @Operation(summary = "Create quiz", description = "Create a new quiz with title, context, level, and initial questions.")
    public ResponseEntity<ApiResponse<QuizStaffResponse>> createQuiz(
            @Valid @RequestBody CreateQuizRequest request) {

        log.info("POST /api/v1/staff/quizzes title={}", request.getTitle());
        UUID userId = UUID.fromString(SecurityUtils.getUserId());
        QuizStaffResponse data = quizService.createQuiz(request, userId);
        return ResponseEntity.ok(ApiResponse.success(data, "Quiz created successfully"));
    }

    /**
     * PUT /staff/quizzes/:quizId
     * Partial update of quiz metadata (not questions).
     */
    @PutMapping("/{quizId}")
    @Operation(summary = "Update quiz metadata", description = "Update title, contextId, or level. All fields are optional.")
    public ResponseEntity<ApiResponse<QuizStaffResponse>> updateQuiz(
            @PathVariable String quizId,
            @RequestBody UpdateQuizRequest request) {

        log.info("PUT /api/v1/staff/quizzes/{}", quizId);
        QuizStaffResponse data = quizService.updateQuiz(quizId, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Quiz updated successfully"));
    }

    /**
     * DELETE /staff/quizzes/:quizId
     * Permanent hard delete.
     */
    @DeleteMapping("/{quizId}")
    @Operation(summary = "Delete quiz", description = "Permanently delete a quiz and all its questions.")
    public ResponseEntity<ApiResponse<Void>> deleteQuiz(
            @PathVariable String quizId) {

        log.info("DELETE /api/v1/staff/quizzes/{}", quizId);
        quizService.deleteQuiz(quizId);
        return ResponseEntity.ok(ApiResponse.success(null, "Quiz deleted successfully"));
    }

    /**
     * PATCH /staff/quizzes/:quizId/soft-delete
     * Sets deletedAt to now.
     */
    @PatchMapping("/{quizId}/soft-delete")
    @Operation(summary = "Soft delete quiz", description = "Move quiz to trash by setting deletedAt timestamp.")
    public ResponseEntity<ApiResponse<Void>> softDeleteQuiz(
            @PathVariable String quizId) {

        log.info("PATCH /api/v1/staff/quizzes/{}/soft-delete", quizId);
        quizService.softDeleteQuiz(quizId);
        return ResponseEntity.ok(ApiResponse.success(null, "Quiz soft-deleted successfully"));
    }

    /**
     * POST /staff/quizzes/:quizId/questions
     * Add a question. Returns the created QuizQuestion.
     */
    @PostMapping("/{quizId}/questions")
    @Operation(summary = "Add question", description = "Add a new question to a quiz. Returns the created question.")
    public ResponseEntity<ApiResponse<QuestionResponse>> addQuestion(
            @PathVariable String quizId,
            @Valid @RequestBody QuestionRequest request) {

        log.info("POST /api/v1/staff/quizzes/{}/questions", quizId);
        QuestionResponse data = quizService.addQuestion(quizId, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Question added successfully"));
    }

    /**
     * PUT /staff/quizzes/:quizId/questions/:questionId
     * Partial update — all fields optional.
     */
    @PutMapping("/{quizId}/questions/{questionId}")
    @Operation(summary = "Update question", description = "Partially update a question. All fields are optional.")
    public ResponseEntity<ApiResponse<Void>> updateQuestion(
            @PathVariable String quizId,
            @PathVariable String questionId,
            @RequestBody QuestionRequest request) {

        log.info("PUT /api/v1/staff/quizzes/{}/questions/{}", quizId, questionId);
        quizService.updateQuestion(quizId, questionId, request);
        return ResponseEntity.ok(ApiResponse.success(null, "Question updated successfully"));
    }

    /**
     * DELETE /staff/quizzes/:quizId/questions/:questionId
     * Hard delete a question.
     */
    @DeleteMapping("/{quizId}/questions/{questionId}")
    @Operation(summary = "Delete question", description = "Permanently delete a question from a quiz.")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(
            @PathVariable String quizId,
            @PathVariable String questionId) {

        log.info("DELETE /api/v1/staff/quizzes/{}/questions/{}", quizId, questionId);
        quizService.deleteQuestion(quizId, questionId);
        return ResponseEntity.ok(ApiResponse.success(null, "Question deleted successfully"));
    }

    /**
     * POST /staff/quizzes/import
     * Bulk-import quizzes from a CSV file.
     * Each distinct quiz title in the CSV becomes one quiz.
     * Rows sharing the same title become the questions for that quiz.
     * Conflicting/invalid quiz groups are skipped and reported in the response.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Import quizzes from CSV",
            description = "Upload a .csv file to bulk-create quizzes. " +
                    "Rows with the same 'title' are grouped into one quiz; each row is one question. " +
                    "Duplicate titles and invalid rows are skipped and reported in the response. " +
                    "All imported quizzes are created as drafts (isPublished = false). " +
                    "Required columns: title, contextId, level, questionContent, " +
                    "option1, option2, option3, option4, correctAnswer, explanation."
    )
    public ResponseEntity<ApiResponse<QuizImportResponse>> importQuizzes(
            @RequestPart("file") MultipartFile file) {

        log.info("POST /api/v1/staff/quizzes/import filename={} size={}",
                file.getOriginalFilename(), file.getSize());
        UUID userId = UUID.fromString(SecurityUtils.getUserId());
        QuizImportResponse data = quizService.importQuizzesFromCsv(file, userId);
        return ResponseEntity.ok(ApiResponse.success(data, "CSV import completed"));
    }

    // ==================== Session History (SYSTEM_ADMIN only) ====================

    /**
     * GET /staff/quizzes/sessions[?userId=...]
     * Without userId  → returns all users' completed sessions (paginated).
     * With    userId  → returns that specific user's completed sessions (paginated).
     */
    @GetMapping("/sessions")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "Get quiz history (admin)",
            description = "Returns paginated completed quiz sessions. " +
                    "Omit userId to get all users' history; provide userId to filter to one user."
    )
    public ResponseEntity<ApiResponse<PaginatedResponse<QuizHistoryResponse>>> getSessionHistory(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("GET /api/v1/staff/quizzes/sessions userId={} page={} size={}", userId, page, size);
        Pageable pageable = PageRequest.of(page, size);
        PaginatedResponse<QuizHistoryResponse> data = userId != null
                ? quizService.getQuizHistoryByUserId(userId, pageable)
                : quizService.getAllUsersQuizHistory(pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Quiz session history retrieved successfully"));
    }

    /**
     * GET /staff/quizzes/sessions/:sessionId
     * Returns the full answer breakdown for any completed session regardless of owner.
     */
    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(
            summary = "Get quiz session detail (admin)",
            description = "Returns the full per-question answer breakdown for any completed quiz session."
    )
    public ResponseEntity<ApiResponse<QuizSessionDetailResponse>> getSessionDetail(
            @PathVariable String sessionId) {

        log.info("GET /api/v1/staff/quizzes/sessions/{}", sessionId);
        // userId = null → admin mode, skip ownership check
        QuizSessionDetailResponse data = quizService.getSessionDetail(sessionId, null);
        return ResponseEntity.ok(ApiResponse.success(data, "Quiz session detail retrieved successfully"));
    }
}
