package com.historytalk.controller.classroom;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.classroom.BatchEnrollResultResponse;
import com.historytalk.dto.classroom.BatchEnrollStudentRequest;
import com.historytalk.dto.classroom.ClassroomStudentResponse;
import com.historytalk.dto.classroom.TransferStudentRequest;
import com.historytalk.dto.school.StudentImportResultDto;
import com.historytalk.dto.school.StudentResponse;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.classroom.ClassroomStudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/classrooms")
@RequiredArgsConstructor
@Tag(name = "SaaS Class Roster Association", description = "Endpoints for managing student membership within classrooms (US-SP6-01)")
public class ClassroomStudentController {

    private final ClassroomStudentService classroomStudentService;

    @PostMapping("/{classId}/students/batch")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    @Operation(summary = "Batch enroll students into classroom (US-SP6-01)")
    public ResponseEntity<ApiResponse<BatchEnrollResultResponse>> batchEnrollStudents(
            @PathVariable UUID classId,
            @Valid @RequestBody BatchEnrollStudentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        BatchEnrollResultResponse response = classroomStudentService.enrollStudentsBatch(classId, request, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Thêm học sinh vào lớp học thành công"));
    }

    @PostMapping(value = "/{classId}/students/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    @Operation(summary = "Upload CSV file to enroll students into classroom (US-SP6-01)")
    public ResponseEntity<ApiResponse<StudentImportResultDto>> importClassroomStudents(
            @PathVariable UUID classId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "defaultInitialToken", required = false) Integer defaultInitialToken,
            @AuthenticationPrincipal UserPrincipal principal) {

        StudentImportResultDto result = classroomStudentService.importClassroomStudents(classId, file, defaultInitialToken, principal);
        String msg = String.format("Nhập dữ liệu thành công: %d học sinh hợp lệ, %d dòng lỗi.",
                result.getSuccessCount(), result.getFailureCount());
        return ResponseEntity.ok(ApiResponse.success(result, msg));
    }

    @GetMapping("/{classId}/students")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    @Operation(summary = "Get list of students in classroom with search and pagination (US-SP6-01)")
    public ResponseEntity<PaginatedApiResponse<ClassroomStudentResponse>> getClassroomStudents(
            @PathVariable UUID classId,
            @Parameter(description = "Keyword to search student by name, studentCode, username, or email")
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 10, sort = "joinedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {

        PaginatedResponse<ClassroomStudentResponse> response = classroomStudentService.getClassroomStudents(
                classId, search, pageable, principal);
        return ResponseEntity.ok(PaginatedApiResponse.of(response, "Danh sách học sinh trong lớp"));
    }

    @GetMapping("/{classId}/available-students")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    @Operation(summary = "Get list of available students in school not yet enrolled in this classroom (US-SP6-01)")
    public ResponseEntity<PaginatedApiResponse<StudentResponse>> getAvailableStudents(
            @PathVariable UUID classId,
            @Parameter(description = "Keyword to search student by name, studentCode, username, or email")
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal) {

        PaginatedResponse<StudentResponse> response = classroomStudentService.getAvailableStudents(
                classId, search, pageable, principal);
        return ResponseEntity.ok(PaginatedApiResponse.of(response, "Danh sách học sinh khả dụng để thêm vào lớp"));
    }

    @DeleteMapping("/{classId}/students/{studentId}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    @Operation(summary = "Remove student from classroom (US-SP6-01)")
    public ResponseEntity<ApiResponse<Void>> removeStudentFromClass(
            @PathVariable UUID classId,
            @PathVariable UUID studentId,
            @AuthenticationPrincipal UserPrincipal principal) {

        classroomStudentService.removeStudentFromClass(classId, studentId, principal);
        return ResponseEntity.ok(ApiResponse.success(null, "Gỡ học sinh khỏi lớp học thành công"));
    }

    @PostMapping("/{fromClassId}/students/transfer")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'TEACHER')")
    @Operation(summary = "Transfer students from one classroom to another (US-SP6-01)")
    public ResponseEntity<ApiResponse<BatchEnrollResultResponse>> transferStudents(
            @PathVariable UUID fromClassId,
            @Valid @RequestBody TransferStudentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {

        BatchEnrollResultResponse response = classroomStudentService.transferStudents(fromClassId, request, principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Chuyển lớp học sinh thành công"));
    }
}
