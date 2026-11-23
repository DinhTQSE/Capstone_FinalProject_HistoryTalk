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
@RequestMapping({"/api/v1/system/trash", "/api/v1/system-trash", "/api/v1/trash"})
@PreAuthorize("hasRole('CONTENT_ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Content Trash", description = "CONTENT_ADMIN trash restore and permanent delete APIs")
public class SystemTrashController {

    private final TrashService trashService;

    @GetMapping("/characters")
    @Operation(summary = "List trashed characters")
    public ResponseEntity<ApiResponse<List<TrashItemResponse>>> getDeletedCharacters() {
        return ResponseEntity.ok(ApiResponse.success(
                trashService.getDeletedCharacters(),
                "Lấy danh sách nhân vật trong thùng rác thành công"));
    }

    @GetMapping("/historical-contexts")
    @Operation(summary = "List trashed historical contexts")
    public ResponseEntity<ApiResponse<List<TrashItemResponse>>> getDeletedContexts() {
        return ResponseEntity.ok(ApiResponse.success(
                trashService.getDeletedContexts(),
                "Lấy danh sách bối cảnh lịch sử trong thùng rác thành công"));
    }

    @GetMapping("/quizzes")
    @Operation(summary = "List trashed quizzes")
    public ResponseEntity<ApiResponse<List<TrashItemResponse>>> getDeletedQuizzes() {
        return ResponseEntity.ok(ApiResponse.success(
                trashService.getDeletedQuizzes(),
                "Lấy danh sách quiz trong thùng rác thành công"));
    }

    @PatchMapping("/characters/restore")
    @Operation(summary = "Restore trashed characters")
    public ResponseEntity<ApiResponse<BulkTrashActionResponse>> restoreCharacters(
            @Valid @RequestBody BulkTrashActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                trashService.restoreCharacters(request.getIds()),
                "Hoàn tất khôi phục nhân vật"));
    }

    @PatchMapping("/historical-contexts/restore")
    @Operation(summary = "Restore trashed historical contexts")
    public ResponseEntity<ApiResponse<BulkTrashActionResponse>> restoreContexts(
            @Valid @RequestBody BulkTrashActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                trashService.restoreContexts(request.getIds()),
                "Hoàn tất khôi phục bối cảnh lịch sử"));
    }

    @PatchMapping("/quizzes/restore")
    @Operation(summary = "Restore trashed quizzes")
    public ResponseEntity<ApiResponse<BulkTrashActionResponse>> restoreQuizzes(
            @Valid @RequestBody BulkTrashActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                trashService.restoreQuizzes(request.getIds()),
                "Hoàn tất khôi phục quiz"));
    }

    @DeleteMapping("/characters")
    @Operation(summary = "Permanently delete trashed characters")
    public ResponseEntity<ApiResponse<BulkTrashActionResponse>> hardDeleteCharacters(
            @Valid @RequestBody BulkTrashActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                trashService.hardDeleteCharacters(request.getIds()),
                "Hoàn tất xóa vĩnh viễn nhân vật"));
    }

    @DeleteMapping("/historical-contexts")
    @Operation(summary = "Permanently delete trashed historical contexts")
    public ResponseEntity<ApiResponse<BulkTrashActionResponse>> hardDeleteContexts(
            @Valid @RequestBody BulkTrashActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                trashService.hardDeleteContexts(request.getIds()),
                "Hoàn tất xóa vĩnh viễn bối cảnh lịch sử"));
    }

    @DeleteMapping("/quizzes")
    @Operation(summary = "Permanently delete trashed quizzes")
    public ResponseEntity<ApiResponse<BulkTrashActionResponse>> hardDeleteQuizzes(
            @Valid @RequestBody BulkTrashActionRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                trashService.hardDeleteQuizzes(request.getIds()),
                "Hoàn tất xóa vĩnh viễn quiz"));
    }
}
