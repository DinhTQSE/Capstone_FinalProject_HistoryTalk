package com.historytalk.service.school.token;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.CreateTokenBoostRequestDto;
import com.historytalk.dto.school.ReviewTokenBoostRequestDto;
import com.historytalk.dto.school.TokenBoostRequestResponse;
import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.TokenBoostRequestStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.school.TokenBoostRequest;
import com.historytalk.entity.user.User;
import com.historytalk.exception.DataConflictException;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.classroom.ClassStudentRepository;
import com.historytalk.repository.school.SchoolRepository;
import com.historytalk.repository.school.TokenBoostRequestRepository;
import com.historytalk.security.UserPrincipal;
import com.historytalk.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenBoostRequestServiceImpl implements TokenBoostRequestService {

    private final TokenBoostRequestRepository tokenBoostRequestRepository;
    private final ClassStudentRepository classStudentRepository;
    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;

    @Override
    @Transactional
    public TokenBoostRequestResponse createBoostRequest(UserPrincipal studentPrincipal, CreateTokenBoostRequestDto request) {
        UserPrincipal principal = resolvePrincipal(studentPrincipal);
        UUID studentUid = UUID.fromString(principal.getUid());

        User student = userRepository.findById(studentUid)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản học sinh"));

        if (student.getRole() != UserRole.SCHOOL_STUDENT || student.getSchool() == null) {
            throw new AccessDeniedException("Chỉ tài khoản học sinh thuộc trường học mới có thể xin cấp thêm token.");
        }

        List<Classroom> activeClassrooms = classStudentRepository.findActiveClassroomsByStudentUid(studentUid);
        if (activeClassrooms.isEmpty()) {
            throw new InvalidRequestException("Bạn hiện chưa thuộc lớp học đang hoạt động nào để gửi yêu cầu xin thêm token.");
        }

        Classroom classroom = activeClassrooms.get(0);
        LocalDate today = LocalDate.now();

        if (tokenBoostRequestRepository.existsByStudent_UidAndRequestDateAndStatus(
                studentUid, today, TokenBoostRequestStatus.PENDING)) {
            throw new DataConflictException("Bạn đã có một yêu cầu xin thêm token đang chờ duyệt trong ngày hôm nay.");
        }

        TokenBoostRequest boostRequest = TokenBoostRequest.builder()
                .school(student.getSchool())
                .classroom(classroom)
                .student(student)
                .requestedTokens(request.getRequestedTokens())
                .reason(request.getReason().trim())
                .status(TokenBoostRequestStatus.PENDING)
                .requestDate(today)
                .build();

        TokenBoostRequest saved = tokenBoostRequestRepository.save(boostRequest);
        log.info("Student {} created daily token boost request {} for {} tokens in class {}",
                student.getUserName(), saved.getId(), saved.getRequestedTokens(), classroom.getClassCode());

        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TokenBoostRequestResponse> getStudentBoostRequests(UserPrincipal studentPrincipal) {
        UserPrincipal principal = resolvePrincipal(studentPrincipal);
        UUID studentUid = UUID.fromString(principal.getUid());
        return tokenBoostRequestRepository.findByStudent_UidOrderByCreatedAtDesc(studentUid)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<TokenBoostRequestResponse> getBoostRequestsForReviewer(
            UserPrincipal reviewerPrincipal, TokenBoostRequestStatus status, Pageable pageable) {
        UserPrincipal principal = resolvePrincipal(reviewerPrincipal);
        UUID reviewerUid = UUID.fromString(principal.getUid());

        Page<TokenBoostRequest> page;
        if (principal.getRole() == UserRole.TEACHER) {
            page = tokenBoostRequestRepository.findByTeacherUidAndStatus(reviewerUid, status, pageable);
        } else if (principal.getRole() == UserRole.SCHOOL_ADMIN) {
            if (principal.getSchoolId() == null) {
                throw new AccessDeniedException("Tài khoản School Admin không gắn với trường học.");
            }
            page = tokenBoostRequestRepository.findBySchoolIdAndStatus(principal.getSchoolId(), status, pageable);
        } else {
            throw new AccessDeniedException("Bạn không có quyền xem danh sách yêu cầu cấp thêm token.");
        }

        List<TokenBoostRequestResponse> content = page.getContent().stream()
                .map(this::mapToResponse)
                .toList();

        return PaginatedResponse.<TokenBoostRequestResponse>builder()
                .content(content)
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    @Override
    @Transactional
    public TokenBoostRequestResponse reviewBoostRequest(
            UserPrincipal reviewerPrincipal, UUID requestId, ReviewTokenBoostRequestDto reviewDto) {
        UserPrincipal principal = resolvePrincipal(reviewerPrincipal);
        UUID reviewerUid = UUID.fromString(principal.getUid());

        User reviewer = userRepository.findById(reviewerUid)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thông tin người duyệt"));

        TokenBoostRequest boostRequest = tokenBoostRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy yêu cầu xin thêm token với ID: " + requestId));

        if (principal.getSchoolId() == null || !principal.getSchoolId().equals(boostRequest.getSchool().getId())) {
            throw new AccessDeniedException("Bạn không có quyền xử lý yêu cầu của trường học khác.");
        }

        if (principal.getRole() == UserRole.TEACHER) {
            User classTeacher = boostRequest.getClassroom().getTeacher();
            if (classTeacher == null || !classTeacher.getUid().equals(reviewerUid)) {
                throw new AccessDeniedException("Bạn không phải giáo viên phụ trách lớp học của học sinh này.");
            }
        } else if (principal.getRole() != UserRole.SCHOOL_ADMIN) {
            throw new AccessDeniedException("Chỉ Giáo viên phụ trách lớp hoặc School Admin mới có quyền duyệt.");
        }

        if (boostRequest.getStatus() != TokenBoostRequestStatus.PENDING) {
            throw new DataConflictException("Yêu cầu này đã được xử lý trước đó (trạng thái: " + boostRequest.getStatus() + ").");
        }

        String action = reviewDto.getAction() != null ? reviewDto.getAction().trim().toUpperCase() : "";
        if ("APPROVE".equals(action) || "APPROVED".equals(action)) {
            int tokensToGrant = reviewDto.getApprovedTokens() != null
                    ? reviewDto.getApprovedTokens()
                    : boostRequest.getRequestedTokens();

            if (tokensToGrant <= 0) {
                throw new InvalidRequestException("Số lượng token duyệt cấp phải lớn hơn 0.");
            }

            School school = boostRequest.getSchool();
            int availablePool = school.getUnallocatedTokenQuota() != null ? school.getUnallocatedTokenQuota() : 0;
            if (availablePool < tokensToGrant) {
                throw new InvalidRequestException(String.format(
                        "Quỹ Token chung của trường không đủ để cấp thêm (yêu cầu: %,d, còn lại: %,d).",
                        tokensToGrant, availablePool));
            }

            school.setUnallocatedTokenQuota(availablePool - tokensToGrant);
            schoolRepository.save(school);

            User student = boostRequest.getStudent();
            int currentStudentToken = student.getToken() != null ? student.getToken() : 0;
            student.setToken(currentStudentToken + tokensToGrant);
            student.setLastTokenResetAt(LocalDateTime.now());
            userRepository.save(student);

            boostRequest.setStatus(TokenBoostRequestStatus.APPROVED);
            boostRequest.setApprovedTokens(tokensToGrant);
            boostRequest.setReviewedBy(reviewer);
            boostRequest.setReviewNote(reviewDto.getReviewNote());

            log.info("Approved token boost request {} by {}: +{} tokens granted to student {}",
                    requestId, reviewer.getUserName(), tokensToGrant, student.getUserName());
        } else if ("REJECT".equals(action) || "REJECTED".equals(action)) {
            boostRequest.setStatus(TokenBoostRequestStatus.REJECTED);
            boostRequest.setApprovedTokens(0);
            boostRequest.setReviewedBy(reviewer);
            boostRequest.setReviewNote(reviewDto.getReviewNote());

            log.info("Rejected token boost request {} by {}", requestId, reviewer.getUserName());
        } else {
            throw new InvalidRequestException("Action không hợp lệ. Vui lòng truyền APPROVE hoặc REJECT.");
        }

        TokenBoostRequest saved = tokenBoostRequestRepository.save(boostRequest);
        return mapToResponse(saved);
    }

    private UserPrincipal resolvePrincipal(UserPrincipal principal) {
        if (principal != null) {
            return principal;
        }
        String userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("Vui lòng đăng nhập để thực hiện thao tác");
        }
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("Không tìm thấy thông tin tài khoản người dùng"));
        return new UserPrincipal(user);
    }

    private TokenBoostRequestResponse mapToResponse(TokenBoostRequest r) {
        return TokenBoostRequestResponse.builder()
                .id(r.getId())
                .schoolId(r.getSchool().getId())
                .classroomId(r.getClassroom().getId())
                .className(r.getClassroom().getClassName())
                .studentUid(r.getStudent().getUid())
                .studentName(r.getStudent().getFullName())
                .studentCode(r.getStudent().getStudentCode())
                .requestedTokens(r.getRequestedTokens())
                .approvedTokens(r.getApprovedTokens())
                .reason(r.getReason())
                .status(r.getStatus())
                .reviewedByUid(r.getReviewedBy() != null ? r.getReviewedBy().getUid() : null)
                .reviewedByName(r.getReviewedBy() != null ? r.getReviewedBy().getFullName() : null)
                .reviewNote(r.getReviewNote())
                .requestDate(r.getRequestDate())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
