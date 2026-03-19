package com.historyTalk.controller.quiz;

import com.historyTalk.dto.ApiResponse;
import com.historyTalk.dto.quiz.*;
import com.historyTalk.dto.PaginatedResponse;
import com.historyTalk.entity.enums.EventEra;
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
@RequestMapping("/api/v1/staff/quizzes")
@PreAuthorize("hasAnyRole('STAFF', 'ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Quizzes (Staff)", description = "Endpoints for staff/admin to manage quizzes")
public class StaffQuizController {

    private final QuizService quizService;

    @GetMapping
    @Operation(summary = "Get all quizzes (paginated)", description = "Retrieve quizzes with optional search, grade, and era filters")
    public ResponseEntity<ApiResponse<PaginatedResponse<QuizStaffResponse>>> getAllQuizzes(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Integer grade,
            @RequestParam(required = false) String era,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        log.info("GET /api/v1/staff/quizzes - search: {}, grade: {}, era: {}, page: {}, size: {}", 
                 search, grade, era, page, size);

        EventEra eraEnum = null;
        if (era != null && !era.isEmpty()) {
            try {
                eraEnum = EventEra.valueOf(era);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(ApiResponse.error("Invalid era value: " + era, "INVALID_ERA"));
            }
        }

        Pageable pageable = PageRequest.of(page, size);
        PaginatedResponse<QuizStaffResponse> data = quizService.getAllQuizzesForStaff(search, grade, eraEnum, pageable);

        return ResponseEntity.ok(ApiResponse.success(data, "Quizzes retrieved successfully"));
    }

    @GetMapping("/{quizId}")
    @Operation(summary = "Get quiz by ID", description = "Retrieve a specific quiz with all questions")
    public ResponseEntity<ApiResponse<QuizStaffResponse>> getQuizById(
            @PathVariable String quizId) {

        log.info("GET /api/v1/staff/quizzes/{} - quiz ID", quizId);

        QuizStaffResponse data = quizService.getQuizByIdForStaff(quizId);

        return ResponseEntity.ok(ApiResponse.success(data, "Quiz retrieved successfully"));
    }

    @PostMapping
    @Operation(summary = "Create new quiz", description = "Create a new quiz with initial questions")
    public ResponseEntity<ApiResponse<QuizStaffResponse>> createQuiz(
            @Valid @RequestBody CreateQuizRequest request) {

        log.info("POST /api/v1/staff/quizzes - title: {}", request.getTitle());

        String userId = SecurityUtils.getUserId();
        QuizStaffResponse data = quizService.createQuiz(request, userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Quiz created successfully"));
    }

    @PutMapping("/{quizId}")
    @Operation(summary = "Update quiz", description = "Update quiz information (not questions)")
    public ResponseEntity<ApiResponse<QuizStaffResponse>> updateQuiz(
            @PathVariable String quizId,
            @Valid @RequestBody UpdateQuizRequest request) {

        log.info("PUT /api/v1/staff/quizzes/{} - update quiz", quizId);

        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        QuizStaffResponse data = quizService.updateQuiz(quizId, request, userId, userRole);

        return ResponseEntity.ok(ApiResponse.success(data, "Quiz updated successfully"));
    }

    @DeleteMapping("/{quizId}")
    @Operation(summary = "Delete quiz", description = "Soft delete a quiz")
    public ResponseEntity<ApiResponse<String>> deleteQuiz(
            @PathVariable String quizId) {

        log.info("DELETE /api/v1/staff/quizzes/{} - delete quiz", quizId);

        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        quizService.deleteQuiz(quizId, userId, userRole);

        return ResponseEntity.ok(ApiResponse.success("", "Quiz deleted successfully"));
    }

    @PostMapping("/{quizId}/questions")
    @Operation(summary = "Add question to quiz", description = "Add a new question to an existing quiz")
    public ResponseEntity<ApiResponse<String>> addQuestion(
            @PathVariable String quizId,
            @Valid @RequestBody QuestionRequest request) {

        log.info("POST /api/v1/staff/quizzes/{}/questions - add question", quizId);

        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        quizService.addQuestion(quizId, request, userId, userRole);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("", "Question added successfully"));
    }

    @PutMapping("/{quizId}/questions/{questionId}")
    @Operation(summary = "Update question", description = "Update a specific question in a quiz")
    public ResponseEntity<ApiResponse<String>> updateQuestion(
            @PathVariable String quizId,
            @PathVariable String questionId,
            @Valid @RequestBody QuestionRequest request) {

        log.info("PUT /api/v1/staff/quizzes/{}/questions/{} - update question", quizId, questionId);

        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        quizService.updateQuestion(quizId, questionId, request, userId, userRole);

        return ResponseEntity.ok(ApiResponse.success("", "Question updated successfully"));
    }

    @DeleteMapping("/{quizId}/questions/{questionId}")
    @Operation(summary = "Delete question", description = "Soft delete a question from a quiz")
    public ResponseEntity<ApiResponse<String>> deleteQuestion(
            @PathVariable String quizId,
            @PathVariable String questionId) {

        log.info("DELETE /api/v1/staff/quizzes/{}/questions/{} - delete question", quizId, questionId);

        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        quizService.deleteQuestion(quizId, questionId, userId, userRole);

        return ResponseEntity.ok(ApiResponse.success("", "Question deleted successfully"));
    }

    @PutMapping("/{quizId}/questions/reorder")
    @Operation(summary = "Reorder questions", description = "Reorder questions in a quiz by providing ordered question IDs")
    public ResponseEntity<ApiResponse<String>> reorderQuestions(
            @PathVariable String quizId,
            @RequestBody List<String> questionIds) {

        log.info("PUT /api/v1/staff/quizzes/{}/questions/reorder - reorder questions", quizId);

        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        quizService.reorderQuestions(quizId, questionIds, userId, userRole);

        return ResponseEntity.ok(ApiResponse.success("", "Questions reordered successfully"));
    }

}
