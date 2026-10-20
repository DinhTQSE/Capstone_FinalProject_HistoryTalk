package com.historytalk.controller.school;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.AcademicYearRolloverRequest;
import com.historytalk.dto.school.AcademicYearRolloverResponse;
import com.historytalk.dto.school.CreateTokenBoostRequestDto;
import com.historytalk.dto.school.DeactivateStudentRequest;
import com.historytalk.dto.school.RenewSchoolSubscriptionRequest;
import com.historytalk.dto.school.ReviewTokenBoostRequestDto;
import com.historytalk.dto.school.SchoolResponse;
import com.historytalk.dto.school.StudentResponse;
import com.historytalk.dto.school.TeacherGrantStudentTokenRequest;
import com.historytalk.dto.school.TokenBoostRequestResponse;
import com.historytalk.dto.school.TopUpSchoolTokensRequest;
import com.historytalk.dto.school.UpdateSchoolTokenPolicyRequest;
import com.historytalk.entity.enums.TokenBoostRequestStatus;
import com.historytalk.security.UserPrincipal;
import com.historytalk.service.school.AcademicLifecycleService;
import com.historytalk.service.school.token.TokenBoostRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "School Subscription, Daily Token Pool & Academic Lifecycle",
        description = "Endpoints for US-SP7-14 to US-SP7-18: Academic subscription (01/09 - 31/05), Daily Token Allowance, Teacher Token Transfer & Ticket Workflow, and Student Lifecycle")
public class SchoolLifecycleAndTokenController {

    private final AcademicLifecycleService academicLifecycleService;
    private final TokenBoostRequestService tokenBoostRequestService;

    // ── US-SP7-14: Subscription Renewal (May), Activation (Sep 1) & Top-up ──

    @PostMapping("/admin/schools/{id}/renew")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Renew school subscription in May for the next academic year (US-SP7-14)")
    public ResponseEntity<ApiResponse<SchoolResponse>> renewSchoolSubscription(
            @PathVariable("id") UUID schoolId,
            @Valid @RequestBody RenewSchoolSubscriptionRequest request) {
        log.info("POST /api/v1/admin/schools/{}/renew", schoolId);
        SchoolResponse response = academicLifecycleService.renewSchoolSubscription(schoolId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "School subscription renewed for next academic year"));
    }

    @PostMapping("/admin/schools/{id}/activate-academic-year")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Activate renewed school subscription on/after 01/09 with up to 20% token rollover (US-SP7-14)")
    public ResponseEntity<ApiResponse<SchoolResponse>> activateRenewedSubscription(
            @PathVariable("id") UUID schoolId,
            @RequestParam(value = "referenceDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate referenceDate) {
        log.info("POST /api/v1/admin/schools/{}/activate-academic-year", schoolId);
        SchoolResponse response = academicLifecycleService.activateRenewedSubscription(schoolId, referenceDate);
        return ResponseEntity.ok(ApiResponse.success(response, "New academic year subscription activated successfully"));
    }

    @PostMapping("/admin/schools/{id}/top-up")
    @PreAuthorize("hasRole('SYSTEM_ADMIN')")
    @Operation(summary = "Top-up additional tokens into school shared pool mid-year (US-SP7-14)")
    public ResponseEntity<ApiResponse<SchoolResponse>> topUpSchoolTokens(
            @PathVariable("id") UUID schoolId,
            @Valid @RequestBody TopUpSchoolTokensRequest request) {
        log.info("POST /api/v1/admin/schools/{}/top-up (+{} tokens)", schoolId, request.getAdditionalTokens());
        SchoolResponse response = academicLifecycleService.topUpSchoolTokens(schoolId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "School token pool topped up successfully"));
    }

    // ── US-SP7-16: Configure Daily Student & Teacher Token Quota Policy ─────

    @PutMapping("/school-admin/token-policy")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Update default daily student/teacher token quota for school, grade level, or classroom (US-SP7-16)")
    public ResponseEntity<ApiResponse<SchoolResponse>> updateSchoolTokenPolicy(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody UpdateSchoolTokenPolicyRequest request) {
        log.info("PUT /api/v1/school-admin/token-policy");
        SchoolResponse response = academicLifecycleService.updateSchoolTokenPolicy(principal, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Daily token quota policy updated successfully"));
    }

    // ── US-SP7-17: Teacher Direct Token Transfer & Ticket Workflow ──────────

    @PostMapping("/student/token-requests")
    @PreAuthorize("hasRole('SCHOOL_STUDENT')")
    @Operation(summary = "Student requests additional daily tokens from classroom teacher (US-SP7-17)")
    public ResponseEntity<ApiResponse<TokenBoostRequestResponse>> createTokenBoostRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateTokenBoostRequestDto request) {
        log.info("POST /api/v1/student/token-requests");
        TokenBoostRequestResponse response = tokenBoostRequestService.createBoostRequest(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Token boost request submitted successfully"));
    }

    @GetMapping("/student/token-requests")
    @PreAuthorize("hasRole('SCHOOL_STUDENT')")
    @Operation(summary = "Student views their token boost request history (US-SP7-17)")
    public ResponseEntity<ApiResponse<List<TokenBoostRequestResponse>>> getStudentTokenBoostRequests(
            @AuthenticationPrincipal UserPrincipal principal) {
        log.info("GET /api/v1/student/token-requests");
        List<TokenBoostRequestResponse> response = tokenBoostRequestService.getStudentBoostRequests(principal);
        return ResponseEntity.ok(ApiResponse.success(response, "Student token boost requests retrieved successfully"));
    }

    @PostMapping("/teacher/students/{studentId}/grant-tokens")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Teacher directly transfers tokens from Teacher's own daily allowance to a student (US-SP7-17)")
    public ResponseEntity<ApiResponse<TokenBoostRequestResponse>> grantTokensFromTeacherToStudent(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("studentId") UUID studentId,
            @Valid @RequestBody TeacherGrantStudentTokenRequest request) {
        log.info("POST /api/v1/teacher/students/{}/grant-tokens (+{} tokens)", studentId, request.getTokens());
        TokenBoostRequestResponse response =
                tokenBoostRequestService.grantTokensFromTeacherToStudent(principal, studentId, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Tokens transferred from teacher daily allowance to student successfully"));
    }

    @PostMapping("/teacher/token-requests")
    @PreAuthorize("hasRole('TEACHER')")
    @Operation(summary = "Teacher creates a ticket to School Admin when Teacher runs out of daily token allowance (US-SP7-17)")
    public ResponseEntity<ApiResponse<TokenBoostRequestResponse>> createTeacherAdminTicket(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateTokenBoostRequestDto request) {
        log.info("POST /api/v1/teacher/token-requests");
        TokenBoostRequestResponse response = tokenBoostRequestService.createTeacherAdminTicket(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Ticket submitted to School Admin successfully"));
    }

    @GetMapping("/teacher/token-requests")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN')")
    @Operation(summary = "Teacher or School Admin views token boost tickets (US-SP7-17)")
    public ResponseEntity<ApiResponse<PaginatedResponse<TokenBoostRequestResponse>>> getTokenBoostRequestsForReviewer(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "status", required = false) TokenBoostRequestStatus status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        log.info("GET /api/v1/teacher/token-requests?status={}", status);
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, limit));
        PaginatedResponse<TokenBoostRequestResponse> response =
                tokenBoostRequestService.getBoostRequestsForReviewer(principal, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response, "Token boost requests retrieved successfully"));
    }

    @PutMapping("/teacher/token-requests/{requestId}/review")
    @PreAuthorize("hasAnyRole('TEACHER', 'SCHOOL_ADMIN')")
    @Operation(summary = "Teacher approves from Teacher's daily allowance (or escalates to School Admin), or School Admin approves from school pool (US-SP7-17)")
    public ResponseEntity<ApiResponse<TokenBoostRequestResponse>> reviewTokenBoostRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("requestId") UUID requestId,
            @Valid @RequestBody ReviewTokenBoostRequestDto reviewDto) {
        log.info("PUT /api/v1/teacher/token-requests/{}/review ({})", requestId, reviewDto.getAction());
        TokenBoostRequestResponse response =
                tokenBoostRequestService.reviewBoostRequest(principal, requestId, reviewDto);
        return ResponseEntity.ok(ApiResponse.success(response, "Token boost request reviewed successfully"));
    }

    @GetMapping("/school-admin/token-requests")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "School Admin views all token boost tickets across the school (US-SP7-17)")
    public ResponseEntity<ApiResponse<PaginatedResponse<TokenBoostRequestResponse>>> getSchoolAdminTokenBoostRequests(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "status", required = false) TokenBoostRequestStatus status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        log.info("GET /api/v1/school-admin/token-requests?status={}", status);
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, limit));
        PaginatedResponse<TokenBoostRequestResponse> response =
                tokenBoostRequestService.getBoostRequestsForReviewer(principal, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response, "School Admin token boost requests retrieved successfully"));
    }

    @PutMapping("/school-admin/token-requests/{requestId}/review")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "School Admin approves or rejects a token boost ticket from school shared pool (US-SP7-17)")
    public ResponseEntity<ApiResponse<TokenBoostRequestResponse>> reviewSchoolAdminTokenBoostRequest(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("requestId") UUID requestId,
            @Valid @RequestBody ReviewTokenBoostRequestDto reviewDto) {
        log.info("PUT /api/v1/school-admin/token-requests/{}/review ({})", requestId, reviewDto.getAction());
        TokenBoostRequestResponse response =
                tokenBoostRequestService.reviewBoostRequest(principal, requestId, reviewDto);
        return ResponseEntity.ok(ApiResponse.success(response, "Token boost request reviewed by School Admin successfully"));
    }

    @GetMapping("/token-requests")
    @PreAuthorize("hasAnyRole('SCHOOL_STUDENT', 'TEACHER', 'SCHOOL_ADMIN')")
    @Operation(summary = "Unified endpoint for all 3 roles (Student, Teacher, School Admin) to view token boost tickets (US-SP7-17)")
    public ResponseEntity<ApiResponse<PaginatedResponse<TokenBoostRequestResponse>>> getUnifiedTokenBoostRequests(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(value = "status", required = false) TokenBoostRequestStatus status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "10") int limit) {
        log.info("GET /api/v1/token-requests?status={}", status);
        Pageable pageable = PageRequest.of(Math.max(0, page - 1), Math.max(1, limit));
        PaginatedResponse<TokenBoostRequestResponse> response =
                tokenBoostRequestService.getBoostRequestsForReviewer(principal, status, pageable);
        return ResponseEntity.ok(ApiResponse.success(response, "Token boost requests retrieved successfully"));
    }

    @GetMapping("/token-requests/{requestId}")
    @PreAuthorize("hasAnyRole('SCHOOL_STUDENT', 'TEACHER', 'SCHOOL_ADMIN')")
    @Operation(summary = "Unified endpoint for all 3 roles (Student, Teacher, School Admin) to view ticket details by ID (US-SP7-17)")
    public ResponseEntity<ApiResponse<TokenBoostRequestResponse>> getTokenBoostRequestById(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("requestId") UUID requestId) {
        log.info("GET /api/v1/token-requests/{}", requestId);
        TokenBoostRequestResponse response = tokenBoostRequestService.getBoostRequestById(principal, requestId);
        return ResponseEntity.ok(ApiResponse.success(response, "Token boost request details retrieved successfully"));
    }

    // ── US-SP7-18: Academic Year Rollover (ARCHIVED) & Student Deactivation ─

    @PostMapping("/school-admin/academic-year/rollover")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Rollover completed academic year: archive old classes, reclaim tokens, and deactivate graduated Grade 12 students (US-SP7-18)")
    public ResponseEntity<ApiResponse<AcademicYearRolloverResponse>> rolloverAcademicYear(
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody AcademicYearRolloverRequest request) {
        log.info("POST /api/v1/school-admin/academic-year/rollover (year={})", request.getCompletedAcademicYear());
        AcademicYearRolloverResponse response = academicLifecycleService.rolloverAcademicYear(principal, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Academic year rollover completed successfully"));
    }

    @PutMapping("/school-admin/students/{uid}/deactivate")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    @Operation(summary = "Deactivate student account upon graduation or school transfer and reclaim tokens (US-SP7-18)")
    public ResponseEntity<ApiResponse<StudentResponse>> deactivateStudentAccount(
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable("uid") UUID studentUid,
            @Valid @RequestBody DeactivateStudentRequest request) {
        log.info("PUT /api/v1/school-admin/students/{}/deactivate (reason={})", studentUid, request.getReason());
        StudentResponse response = academicLifecycleService.deactivateStudentAccount(principal, studentUid, request);
        return ResponseEntity.ok(ApiResponse.success(response, "Student account deactivated and login blocked"));
    }
}
