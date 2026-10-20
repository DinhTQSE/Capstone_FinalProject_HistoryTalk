package com.historytalk.service.school.token;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.CreateTokenBoostRequestDto;
import com.historytalk.dto.school.ReviewTokenBoostRequestDto;
import com.historytalk.dto.school.TeacherGrantStudentTokenRequest;
import com.historytalk.dto.school.TokenBoostRequestResponse;
import com.historytalk.entity.enums.TokenBoostRequestStatus;
import com.historytalk.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface TokenBoostRequestService {

    TokenBoostRequestResponse createBoostRequest(UserPrincipal studentPrincipal, CreateTokenBoostRequestDto request);

    TokenBoostRequestResponse createTeacherAdminTicket(UserPrincipal teacherPrincipal, CreateTokenBoostRequestDto request);

    TokenBoostRequestResponse grantTokensFromTeacherToStudent(
            UserPrincipal teacherPrincipal, UUID studentUid, TeacherGrantStudentTokenRequest request);

    List<TokenBoostRequestResponse> getStudentBoostRequests(UserPrincipal studentPrincipal);

    PaginatedResponse<TokenBoostRequestResponse> getBoostRequestsForReviewer(
            UserPrincipal reviewerPrincipal, TokenBoostRequestStatus status, Pageable pageable);

    TokenBoostRequestResponse getBoostRequestById(UserPrincipal principal, UUID requestId);

    TokenBoostRequestResponse reviewBoostRequest(
            UserPrincipal reviewerPrincipal, UUID requestId, ReviewTokenBoostRequestDto reviewDto);
}
