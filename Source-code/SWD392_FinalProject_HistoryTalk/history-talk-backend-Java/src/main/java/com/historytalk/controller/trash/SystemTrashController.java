package com.historytalk.controller.trash;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.trash.BulkTrashActionRequest;
import com.historytalk.dto.trash.BulkTrashActionResponse;
import com.historytalk.dto.trash.TrashItemResponse;
import com.historytalk.service.trash.TrashService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/system/trash")
@PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Content Trash", description = "CONTENT_ADMIN and SYSTEM_ADMIN trash restore and permanent delete APIs")
public class SystemTrashController {

    private final TrashService trashService;

    @GetMapping("/characters")
    @Operation(summary = "List trashed characters")
    public ResponseEntity<ApiResponse<List<TrashItemResponse>>> getDeletedCharacters() {
        return ResponseEntity.ok(ApiResponse.success(
                trashService.getDeletedCharacters(),
                "Trashed characters retrieved successfully"));
    }

    @GetMapping("/historical-contexts")
    @Operation(summary = "List trashed historical contexts")
    public ResponseEntity<ApiResponse<List<TrashItemResponse>>> getDeletedContexts() {
        return ResponseEntity.ok(ApiResponse.success(
                trashService.getDeletedContexts(),
                "Trashed historical contexts retrieved successfully"));
    }

    @GetMapping("/quizzes")
    @Operation(summary = "List trashed quizzes")
    public ResponseEntity<ApiResponse<List<TrashItemResponse>>> getDeletedQuizzes() {
        return ResponseEntity.ok(ApiResponse.success(
                trashService.getDeletedQuizzes(),
                "Trashed quizzes retrieved successfully"));
    }

    @PatchMapping("/characters/restore")
    @Operation(summary = "Restore trashed characters")
    public ResponseEntity<ApiResponse<BulkTrashActionResponse>> restoreCharacters(
            @Valid @RequestBody BulkTrashActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                trashService.restoreCharacters(request.getIds()),
                "Character restore completed"));
    }

    @PatchMapping("/historical-contexts/restore")
    @Operation(summary = "Restore trashed historical contexts")
    public ResponseEntity<ApiResponse<BulkTrashActionResponse>> restoreContexts(
            @Valid @RequestBody BulkTrashActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                trashService.restoreContexts(request.getIds()),
                "Historical context restore completed"));
    }

    @PatchMapping("/quizzes/restore")
    @Operation(summary = "Restore trashed quizzes")
    public ResponseEntity<ApiResponse<BulkTrashActionResponse>> restoreQuizzes(
            @Valid @RequestBody BulkTrashActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                trashService.restoreQuizzes(request.getIds()),
                "Quiz restore completed"));
    }

    @DeleteMapping("/characters")
    @Operation(summary = "Permanently delete trashed characters")
    public ResponseEntity<ApiResponse<BulkTrashActionResponse>> hardDeleteCharacters(
            @Valid @RequestBody BulkTrashActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                trashService.hardDeleteCharacters(request.getIds()),
                "Character hard delete completed"));
    }

    @DeleteMapping("/historical-contexts")
    @Operation(summary = "Permanently delete trashed historical contexts")
    public ResponseEntity<ApiResponse<BulkTrashActionResponse>> hardDeleteContexts(
            @Valid @RequestBody BulkTrashActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                trashService.hardDeleteContexts(request.getIds()),
                "Historical context hard delete completed"));
    }

    @DeleteMapping("/quizzes")
    @Operation(summary = "Permanently delete trashed quizzes")
    public ResponseEntity<ApiResponse<BulkTrashActionResponse>> hardDeleteQuizzes(
            @Valid @RequestBody BulkTrashActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                trashService.hardDeleteQuizzes(request.getIds()),
                "Quiz hard delete completed"));
    }
}
