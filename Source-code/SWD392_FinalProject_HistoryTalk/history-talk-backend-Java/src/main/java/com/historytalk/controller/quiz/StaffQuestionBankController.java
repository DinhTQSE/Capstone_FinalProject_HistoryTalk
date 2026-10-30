package com.historytalk.controller.quiz;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.quiz.QuestionBankResponse;
import com.historytalk.dto.quiz.QuestionRequest;
import com.historytalk.service.quiz.QuizService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/staff/question-bank")
@PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Question Bank (Staff)", description = "Manage reusable bank questions independently of quizzes")
public class StaffQuestionBankController {

    private final QuizService quizService;

    @GetMapping
    @Operation(summary = "List/search question bank")
    public ResponseEntity<ApiResponse<PaginatedResponse<QuestionBankResponse>>> getQuestionBank(
            @RequestParam(required = false) String questionType,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        log.info("GET /api/v1/staff/question-bank type={} difficulty={} search={}", questionType, difficulty, search);
        Pageable pageable = PageRequest.of(page, size);
        PaginatedResponse<QuestionBankResponse> data = quizService.getQuestionBank(questionType, difficulty, search, pageable);
        return ResponseEntity.ok(ApiResponse.success(data, "Lấy danh sách câu hỏi thành công"));
    }

    @PostMapping
    @Operation(summary = "Create a standalone bank question (no quiz required)")
    public ResponseEntity<ApiResponse<QuestionBankResponse>> createBankQuestion(
            @Valid @RequestBody QuestionRequest request) {

        log.info("POST /api/v1/staff/question-bank type={}", request.getQuestionType());
        QuestionBankResponse data = quizService.createBankQuestion(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(data, "Tạo câu hỏi thành công"));
    }

    @PutMapping("/{questionId}")
    @Operation(summary = "Update a bank question")
    public ResponseEntity<ApiResponse<QuestionBankResponse>> updateBankQuestion(
            @PathVariable String questionId,
            @Valid @RequestBody QuestionRequest request) {

        log.info("PUT /api/v1/staff/question-bank/{}", questionId);
        QuestionBankResponse data = quizService.updateBankQuestion(questionId, request);
        return ResponseEntity.ok(ApiResponse.success(data, "Cập nhật câu hỏi thành công"));
    }

    @PatchMapping("/{questionId}/soft-delete")
    @Operation(summary = "Soft-delete a bank question")
    public ResponseEntity<ApiResponse<Void>> softDeleteBankQuestion(@PathVariable String questionId) {
        log.info("PATCH /api/v1/staff/question-bank/{}/soft-delete", questionId);
        quizService.softDeleteBankQuestion(questionId);
        return ResponseEntity.ok(ApiResponse.success(null, "Xóa câu hỏi thành công"));
    }

    @PostMapping("/{questionId}/attach/{quizId}")
    @Operation(summary = "Attach an existing bank question to a quiz")
    public ResponseEntity<ApiResponse<Void>> attachQuestionToQuiz(
            @PathVariable String questionId,
            @PathVariable String quizId) {

        log.info("POST /api/v1/staff/question-bank/{}/attach/{}", questionId, quizId);
        quizService.attachQuestionToQuiz(quizId, questionId);
        return ResponseEntity.ok(ApiResponse.success(null, "Liên kết câu hỏi với quiz thành công"));
    }

    @DeleteMapping("/{questionId}/detach/{quizId}")
    @Operation(summary = "Unlink a bank question from a quiz (question is kept in the bank)")
    public ResponseEntity<ApiResponse<Void>> unlinkQuestionFromQuiz(
            @PathVariable String questionId,
            @PathVariable String quizId) {

        log.info("DELETE /api/v1/staff/question-bank/{}/detach/{}", questionId, quizId);
        quizService.unlinkQuestionFromQuiz(quizId, questionId);
        return ResponseEntity.ok(ApiResponse.success(null, "Hủy liên kết câu hỏi thành công"));
    }
}
