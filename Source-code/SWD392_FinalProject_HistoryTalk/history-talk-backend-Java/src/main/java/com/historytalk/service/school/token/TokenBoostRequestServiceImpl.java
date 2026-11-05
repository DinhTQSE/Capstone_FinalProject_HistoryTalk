package com.historytalk.service.school.token;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.CreateTokenBoostRequestDto;
import com.historytalk.dto.school.ReviewTokenBoostRequestDto;
import com.historytalk.dto.school.TeacherGrantStudentTokenRequest;
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
import com.historytalk.repository.classroom.ClassroomRepository;
import com.historytalk.repository.school.SchoolRepository;
import com.historytalk.repository.school.TokenBoostRequestRepository;
import com.historytalk.security.UserPrincipal;
import com.historytalk.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

    private static final int DEFAULT_DAILY_TEACHER_QUOTA = 10000;
    private static final int DEFAULT_DAILY_STUDENT_QUOTA = 1000;

    private final TokenBoostRequestRepository tokenBoostRequestRepository;
    private final ClassStudentRepository classStudentRepository;
    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;

    @Autowired(required = false)
    private ClassroomRepository classroomRepository;

    @Override
    @Transactional
    public TokenBoostRequestResponse createBoostRequest(UserPrincipal studentPrincipal, CreateTokenBoostRequestDto request) {
        UserPrincipal principal = resolvePrincipal(studentPrincipal);
        UUID studentUid = UUID.fromString(principal.getUid());

        User student = userRepository.findById(studentUid)
                .orElseThrow(() -> new ResourceNotFoundException("KhÃ´ng tÃ¬m tháº¥y tÃ i khoáº£n há»c sinh"));

        if (student.getRole() != UserRole.SCHOOL_STUDENT || student.getSchool() == null) {
            throw new AccessDeniedException("Chá»‰ tÃ i khoáº£n há»c sinh thuá»™c trÆ°á»ng há»c má»›i cÃ³ thá»ƒ xin cáº¥p thÃªm token.");
        }

        List<Classroom> activeClassrooms = classStudentRepository.findActiveClassroomsByStudentUid(studentUid);
        if (activeClassrooms.isEmpty()) {
            throw new InvalidRequestException("Báº¡n hiá»‡n chÆ°a thuá»™c lá»›p há»c Ä‘ang hoáº¡t Ä‘á»™ng nÃ o Ä‘á»ƒ gá»­i yÃªu cáº§u xin thÃªm token.");
        }

        Classroom classroom = activeClassrooms.get(0);
        LocalDate today = LocalDate.now();

        if (tokenBoostRequestRepository.existsByStudent_UidAndRequestDateAndStatus(
                studentUid, today, TokenBoostRequestStatus.PENDING)) {
            throw new DataConflictException("Báº¡n Ä‘Ã£ cÃ³ má»™t yÃªu cáº§u xin thÃªm token Ä‘ang chá» duyá»‡t trong ngÃ y hÃ´m nay.");
        }

        TokenBoostRequest boostRequest = TokenBoostRequest.builder()
                .school(student.getSchool())
                .classroom(classroom)
                .student(student)
                .requester(student)
                .requesterRole(UserRole.SCHOOL_STUDENT)
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
    @Transactional
    public TokenBoostRequestResponse createTeacherAdminTicket(UserPrincipal teacherPrincipal, CreateTokenBoostRequestDto request) {
        UserPrincipal principal = resolvePrincipal(teacherPrincipal);
        UUID teacherUid = UUID.fromString(principal.getUid());

        User teacher = userRepository.findById(teacherUid)
                .orElseThrow(() -> new ResourceNotFoundException("KhÃ´ng tÃ¬m tháº¥y tÃ i khoáº£n giÃ¡o viÃªn"));

        if (teacher.getRole() != UserRole.TEACHER || teacher.getSchool() == null) {
            throw new AccessDeniedException("Chá»‰ giÃ¡o viÃªn thuá»™c trÆ°á»ng há»c má»›i cÃ³ thá»ƒ táº¡o ticket gá»­i School Admin.");
        }

        School school = teacher.getSchool();
        User targetStudent = null;
        Classroom targetClassroom = null;

        if (request.getStudentId() != null) {
            targetStudent = userRepository.findById(request.getStudentId())
                    .orElseThrow(() -> new ResourceNotFoundException("KhÃ´ng tÃ¬m tháº¥y há»c sinh vá»›i ID: " + request.getStudentId()));
            if (targetStudent.getSchool() == null || !school.getId().equals(targetStudent.getSchool().getId())
                    || targetStudent.getRole() != UserRole.SCHOOL_STUDENT) {
                throw new InvalidRequestException("Há»c sinh khÃ´ng thuá»™c trÆ°á»ng cá»§a báº¡n.");
            }
            List<Classroom> activeClasses = classStudentRepository.findActiveClassroomsByStudentUid(targetStudent.getUid());
            if (!activeClasses.isEmpty()) {
                targetClassroom = activeClasses.get(0);
            }
        }

        if (targetClassroom == null && request.getClassroomId() != null && classroomRepository != null) {
            targetClassroom = classroomRepository.findByIdAndSchoolIdAndDeletedAtIsNull(request.getClassroomId(), school.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("KhÃ´ng tÃ¬m tháº¥y lá»›p há»c vá»›i ID: " + request.getClassroomId()));
        }

        LocalDate today = LocalDate.now();
        TokenBoostRequest ticket = TokenBoostRequest.builder()
                .school(school)
                .classroom(targetClassroom)
                .student(targetStudent != null ? targetStudent : teacher)
                .requester(teacher)
                .requesterRole(UserRole.TEACHER)
                .requestedTokens(request.getRequestedTokens())
                .reason(request.getReason().trim())
                .status(TokenBoostRequestStatus.PENDING_ADMIN)
                .teacherReviewedBy(teacher)
                .teacherReviewNote("GiÃ¡o viÃªn táº¡o ticket xin School Admin cáº¥p thÃªm token")
                .teacherReviewedAt(LocalDateTime.now())
                .requestDate(today)
                .build();

        TokenBoostRequest saved = tokenBoostRequestRepository.save(ticket);
        log.info("Teacher {} created ticket {} to School Admin for {} tokens (target={})",
                teacher.getUserName(), saved.getId(), saved.getRequestedTokens(),
                targetStudent != null ? targetStudent.getUserName() : teacher.getUserName());

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public TokenBoostRequestResponse grantTokensFromTeacherToStudent(
            UserPrincipal teacherPrincipal, UUID studentUid, TeacherGrantStudentTokenRequest request) {
        UserPrincipal principal = resolvePrincipal(teacherPrincipal);
        UUID teacherUid = UUID.fromString(principal.getUid());

        User teacher = userRepository.findById(teacherUid)
                .orElseThrow(() -> new ResourceNotFoundException("KhÃ´ng tÃ¬m tháº¥y tÃ i khoáº£n giÃ¡o viÃªn"));

        if (teacher.getRole() != UserRole.TEACHER || teacher.getSchool() == null) {
            throw new AccessDeniedException("Chá»‰ giÃ¡o viÃªn má»›i cÃ³ quyá»n cáº¥p trá»±c tiáº¿p token tá»« quá»¹ giÃ¡o viÃªn cho há»c sinh.");
        }

        School school = teacher.getSchool();
        User student = userRepository.findById(studentUid)
                .orElseThrow(() -> new ResourceNotFoundException("KhÃ´ng tÃ¬m tháº¥y há»c sinh vá»›i ID: " + studentUid));

        if (student.getSchool() == null || !school.getId().equals(student.getSchool().getId())
                || student.getRole() != UserRole.SCHOOL_STUDENT) {
            throw new AccessDeniedException("Há»c sinh khÃ´ng thuá»™c trÆ°á»ng cá»§a báº¡n.");
        }

        List<Classroom> activeClasses = classStudentRepository.findActiveClassroomsByStudentUid(studentUid);
        Classroom teacherClass = activeClasses.stream()
                .filter(c -> c.getTeacher() != null && c.getTeacher().getUid().equals(teacherUid))
                .findFirst()
                .orElseThrow(() -> new AccessDeniedException("Báº¡n khÃ´ng pháº£i giÃ¡o viÃªn phá»¥ trÃ¡ch lá»›p há»c cá»§a há»c sinh nÃ y."));

        int tokensToGrant = request.getTokens();
        transferFromTeacherAllowanceToStudent(teacher, student, teacherClass, school, tokensToGrant);

        LocalDate today = LocalDate.now();
        String reason = (request.getReason() != null && !request.getReason().isBlank())
                ? request.getReason().trim()
                : "GiÃ¡o viÃªn cáº¥p trá»±c tiáº¿p token tá»« háº¡n má»©c giÃ¡o viÃªn trong ngÃ y";

        TokenBoostRequest directGrantRecord = TokenBoostRequest.builder()
                .school(school)
                .classroom(teacherClass)
                .student(student)
                .requester(teacher)
                .requesterRole(UserRole.TEACHER)
                .requestedTokens(tokensToGrant)
                .approvedTokens(tokensToGrant)
                .reason(reason)
                .status(TokenBoostRequestStatus.APPROVED)
                .teacherReviewedBy(teacher)
                .teacherReviewNote(reason)
                .teacherReviewedAt(LocalDateTime.now())
                .reviewedBy(teacher)
                .reviewNote(reason)
                .requestDate(today)
                .build();

        TokenBoostRequest saved = tokenBoostRequestRepository.save(directGrantRecord);
        log.info("Teacher {} directly transferred {} daily tokens to student {} in class {}",
                teacher.getUserName(), tokensToGrant, student.getUserName(), teacherClass.getClassCode());

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
                throw new AccessDeniedException("TÃ i khoáº£n School Admin khÃ´ng gáº¯n vá»›i trÆ°á»ng há»c.");
            }
            page = tokenBoostRequestRepository.findBySchoolIdAndStatus(principal.getSchoolId(), status, pageable);
        } else if (principal.getRole() == UserRole.SCHOOL_STUDENT) {
            List<TokenBoostRequest> studentReqs = tokenBoostRequestRepository.findByStudent_UidOrderByCreatedAtDesc(reviewerUid)
                    .stream()
                    .filter(r -> status == null || r.getStatus() == status)
                    .toList();
            page = new PageImpl<>(studentReqs, pageable, studentReqs.size());
        } else {
            throw new AccessDeniedException("Báº¡n khÃ´ng cÃ³ quyá»n xem danh sÃ¡ch yÃªu cáº§u cáº¥p thÃªm token.");
        }

        List<TokenBoostRequestResponse> content = page.getContent().stream()
                .map(this::mapToResponse)
                .toList();

        return PaginatedResponse.<TokenBoostRequestResponse>builder()
                .content(content)
                .currentPage(page.getNumber() + 1)
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public TokenBoostRequestResponse getBoostRequestById(UserPrincipal userPrincipal, UUID requestId) {
        UserPrincipal principal = resolvePrincipal(userPrincipal);
        UUID currentUid = UUID.fromString(principal.getUid());

        TokenBoostRequest req = tokenBoostRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("KhÃ´ng tÃ¬m tháº¥y ticket vá»›i ID: " + requestId));

        if (principal.getSchoolId() == null || !principal.getSchoolId().equals(req.getSchool().getId())) {
            throw new AccessDeniedException("Báº¡n khÃ´ng cÃ³ quyá»n xem ticket cá»§a trÆ°á»ng há»c khÃ¡c.");
        }

        if (principal.getRole() == UserRole.SCHOOL_STUDENT) {
            boolean isStudentTarget = req.getStudent() != null && currentUid.equals(req.getStudent().getUid());
            boolean isRequester = req.getRequester() != null && currentUid.equals(req.getRequester().getUid());
            if (!isStudentTarget && !isRequester) {
                throw new AccessDeniedException("Báº¡n chá»‰ cÃ³ thá»ƒ xem cÃ¡c ticket yÃªu cáº§u token cá»§a chÃ­nh mÃ¬nh.");
            }
        } else if (principal.getRole() == UserRole.TEACHER) {
            boolean isClassTeacher = req.getClassroom() != null
                    && req.getClassroom().getTeacher() != null
                    && currentUid.equals(req.getClassroom().getTeacher().getUid());
            boolean isRequester = req.getRequester() != null && currentUid.equals(req.getRequester().getUid());
            if (!isClassTeacher && !isRequester) {
                throw new AccessDeniedException("Báº¡n chá»‰ cÃ³ thá»ƒ xem cÃ¡c ticket thuá»™c lá»›p mÃ¬nh phá»¥ trÃ¡ch hoáº·c do mÃ¬nh táº¡o.");
            }
        }

        return mapToResponse(req);
    }

    @Override
    @Transactional
    public TokenBoostRequestResponse reviewBoostRequest(
            UserPrincipal reviewerPrincipal, UUID requestId, ReviewTokenBoostRequestDto reviewDto) {
        UserPrincipal principal = resolvePrincipal(reviewerPrincipal);
        UUID reviewerUid = UUID.fromString(principal.getUid());

        User reviewer = userRepository.findById(reviewerUid)
                .orElseThrow(() -> new ResourceNotFoundException("KhÃ´ng tÃ¬m tháº¥y thÃ´ng tin ngÆ°á»i duyá»‡t"));

        TokenBoostRequest boostRequest = tokenBoostRequestRepository.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("KhÃ´ng tÃ¬m tháº¥y yÃªu cáº§u xin thÃªm token vá»›i ID: " + requestId));

        if (principal.getSchoolId() == null || !principal.getSchoolId().equals(boostRequest.getSchool().getId())) {
            throw new AccessDeniedException("Báº¡n khÃ´ng cÃ³ quyá»n xá»­ lÃ½ yÃªu cáº§u cá»§a trÆ°á»ng há»c khÃ¡c.");
        }

        if (principal.getRole() == UserRole.TEACHER) {
            User classTeacher = boostRequest.getClassroom() != null ? boostRequest.getClassroom().getTeacher() : null;
            if (classTeacher == null || !classTeacher.getUid().equals(reviewerUid)) {
                throw new AccessDeniedException("Báº¡n khÃ´ng pháº£i giÃ¡o viÃªn phá»¥ trÃ¡ch lá»›p há»c cá»§a há»c sinh nÃ y.");
            }
            if (boostRequest.getStatus() != TokenBoostRequestStatus.PENDING) {
                throw new DataConflictException("YÃªu cáº§u nÃ y Ä‘Ã£ Ä‘Æ°á»£c xá»­ lÃ½ hoáº·c Ä‘Ã£ chuyá»ƒn lÃªn School Admin (tráº¡ng thÃ¡i: " + boostRequest.getStatus() + ").");
            }
        } else if (principal.getRole() == UserRole.SCHOOL_ADMIN) {
            if (boostRequest.getStatus() != TokenBoostRequestStatus.PENDING
                    && boostRequest.getStatus() != TokenBoostRequestStatus.PENDING_ADMIN) {
                throw new DataConflictException("YÃªu cáº§u nÃ y Ä‘Ã£ Ä‘Æ°á»£c xá»­ lÃ½ trÆ°á»›c Ä‘Ã³ (tráº¡ng thÃ¡i: " + boostRequest.getStatus() + ").");
            }
        } else {
            throw new AccessDeniedException("Chá»‰ GiÃ¡o viÃªn phá»¥ trÃ¡ch lá»›p hoáº·c School Admin má»›i cÃ³ quyá»n duyá»‡t.");
        }

        String action = reviewDto.getAction() != null ? reviewDto.getAction().trim().toUpperCase() : "";
        if ("APPROVE".equals(action) || "APPROVED".equals(action)) {
            int tokensToGrant = reviewDto.getApprovedTokens() != null
                    ? reviewDto.getApprovedTokens()
                    : boostRequest.getRequestedTokens();

            if (tokensToGrant <= 0) {
                throw new InvalidRequestException("Sá»‘ lÆ°á»£ng token duyá»‡t cáº¥p pháº£i lá»›n hÆ¡n 0.");
            }

            School school = boostRequest.getSchool();
            User targetUser = boostRequest.getStudent() != null ? boostRequest.getStudent() : boostRequest.getRequester();
            if (targetUser == null) {
                throw new InvalidRequestException("KhÃ´ng xÃ¡c Ä‘á»‹nh Ä‘Æ°á»£c ngÆ°á»i nháº­n token cá»§a yÃªu cáº§u nÃ y.");
            }

            if (principal.getRole() == UserRole.TEACHER) {
                // Teacher approves directly by transferring from Teacher's own daily allowance to Student
                transferFromTeacherAllowanceToStudent(reviewer, targetUser, boostRequest.getClassroom(), school, tokensToGrant);

                boostRequest.setTeacherReviewedBy(reviewer);
                boostRequest.setTeacherReviewNote(reviewDto.getReviewNote());
                boostRequest.setTeacherReviewedAt(LocalDateTime.now());
            } else {
                // School Admin approves from school's shared pool (for Student or for Teacher who ran out of quota)
                int availablePool = school.getUnallocatedTokenQuota() != null ? school.getUnallocatedTokenQuota() : 0;
                if (availablePool < tokensToGrant) {
                    throw new InvalidRequestException(String.format(
                            "Quá»¹ Token chung cá»§a trÆ°á»ng khÃ´ng Ä‘á»§ Ä‘á»ƒ cáº¥p thÃªm háº¡n má»©c (yÃªu cáº§u: %,d, cÃ²n láº¡i trong quá»¹: %,d).",
                            tokensToGrant, availablePool));
                }

                grantDailyBonusFromSchoolPool(targetUser, boostRequest.getClassroom(), school, tokensToGrant, availablePool);

                boostRequest.setAdminReviewedBy(reviewer);
                boostRequest.setAdminReviewNote(reviewDto.getReviewNote());
                boostRequest.setAdminReviewedAt(LocalDateTime.now());
            }

            boostRequest.setStatus(TokenBoostRequestStatus.APPROVED);
            boostRequest.setApprovedTokens(tokensToGrant);
            boostRequest.setReviewedBy(reviewer);
            boostRequest.setReviewNote(reviewDto.getReviewNote());

            log.info("Approved token boost request {} by {} ({}): +{} daily bonus tokens granted to {}",
                    requestId, reviewer.getUserName(), principal.getRole(), tokensToGrant, targetUser.getUserName());
        } else if ("ESCALATE".equals(action) || "PENDING_ADMIN".equals(action)) {
            if (principal.getRole() != UserRole.TEACHER) {
                throw new InvalidRequestException("Chá»‰ GiÃ¡o viÃªn má»›i sá»­ dá»¥ng thao tÃ¡c chuyá»ƒn tiáº¿p (ESCALATE) ticket lÃªn School Admin.");
            }
            if (reviewDto.getApprovedTokens() != null && reviewDto.getApprovedTokens() > 0) {
                boostRequest.setRequestedTokens(reviewDto.getApprovedTokens());
            }
            boostRequest.setStatus(TokenBoostRequestStatus.PENDING_ADMIN);
            boostRequest.setTeacherReviewedBy(reviewer);
            boostRequest.setTeacherReviewNote(reviewDto.getReviewNote() != null
                    ? reviewDto.getReviewNote()
                    : "GiÃ¡o viÃªn Ä‘Ã£ háº¿t háº¡n má»©c ngÃ y, chuyá»ƒn ticket lÃªn School Admin duyá»‡t cáº¥p thÃªm");
            boostRequest.setTeacherReviewedAt(LocalDateTime.now());
            boostRequest.setReviewedBy(reviewer);
            boostRequest.setReviewNote(boostRequest.getTeacherReviewNote());

            log.info("Teacher {} escalated token boost request {} to School Admin (PENDING_ADMIN)", reviewer.getUserName(), requestId);
        } else if ("REJECT".equals(action) || "REJECTED".equals(action)) {
            boostRequest.setStatus(TokenBoostRequestStatus.REJECTED);
            boostRequest.setApprovedTokens(0);
            if (principal.getRole() == UserRole.TEACHER) {
                boostRequest.setTeacherReviewedBy(reviewer);
                boostRequest.setTeacherReviewNote(reviewDto.getReviewNote());
                boostRequest.setTeacherReviewedAt(LocalDateTime.now());
            } else {
                boostRequest.setAdminReviewedBy(reviewer);
                boostRequest.setAdminReviewNote(reviewDto.getReviewNote());
                boostRequest.setAdminReviewedAt(LocalDateTime.now());
            }
            boostRequest.setReviewedBy(reviewer);
            boostRequest.setReviewNote(reviewDto.getReviewNote());

            log.info("Rejected token boost request {} by {}", requestId, reviewer.getUserName());
        } else {
            throw new InvalidRequestException("Action khÃ´ng há»£p lá»‡. Vui lÃ²ng truyá»n APPROVE, REJECT hoáº·c ESCALATE.");
        }

        TokenBoostRequest saved = tokenBoostRequestRepository.save(boostRequest);
        return mapToResponse(saved);
    }

    private void transferFromTeacherAllowanceToStudent(
            User teacher, User student, Classroom classroom, School school, int tokensToGrant) {
        int availablePool = school.getUnallocatedTokenQuota() != null ? school.getUnallocatedTokenQuota() : 0;
        if (availablePool < tokensToGrant) {
            throw new InvalidRequestException(String.format(
                    "Quá»¹ Token chung cá»§a trÆ°á»ng khÃ´ng Ä‘á»§ Ä‘á»ƒ cáº¥p thÃªm háº¡n má»©c (yÃªu cáº§u: %,d, cÃ²n láº¡i trong quá»¹: %,d).",
                    tokensToGrant, availablePool));
        }

        LocalDate today = LocalDate.now();

        // Prepare teacher daily allowance
        if (teacher.getLastTokenResetAt() == null || teacher.getLastTokenResetAt().toLocalDate().isBefore(today)) {
            teacher.setDailyTokensUsed(0);
            teacher.setDailyBonusTokens(0);
            teacher.setLastTokenResetAt(LocalDateTime.now());
        }
        int teacherBaseDaily = (school.getDefaultDailyTeacherQuota() != null && school.getDefaultDailyTeacherQuota() > 0)
                ? school.getDefaultDailyTeacherQuota()
                : DEFAULT_DAILY_TEACHER_QUOTA;
        int teacherBonusToday = teacher.getDailyBonusTokens() != null ? Math.max(0, teacher.getDailyBonusTokens()) : 0;
        int teacherUsedToday = teacher.getDailyTokensUsed() != null ? Math.max(0, teacher.getDailyTokensUsed()) : 0;
        int teacherRemainingToday = Math.min(Math.max(0, (teacherBaseDaily + teacherBonusToday) - teacherUsedToday), availablePool);

        if (teacherRemainingToday < tokensToGrant) {
            throw new InvalidRequestException(String.format(
                    "Háº¡n má»©c Token trong ngÃ y cá»§a GiÃ¡o viÃªn khÃ´ng Ä‘á»§ Ä‘á»ƒ cáº¥p cho há»c sinh (cáº§n cáº¥p: %,d, cÃ²n láº¡i cá»§a GiÃ¡o viÃªn: %,d). " +
                    "Vui lÃ²ng táº¡o Ticket hoáº·c báº¥m chuyá»ƒn tiáº¿p (ESCALATE) lÃªn School Admin Ä‘á»ƒ duyá»‡t cáº¥p thÃªm.",
                    tokensToGrant, teacherRemainingToday));
        }

        // Deduct from teacher's daily allowance (transferred to student, NOT deducted from school shared pool until student chats)
        int updatedTeacherUsed = teacherUsedToday + tokensToGrant;
        teacher.setDailyTokensUsed(updatedTeacherUsed);
        int updatedTeacherRemaining = Math.min(Math.max(0, (teacherBaseDaily + teacherBonusToday) - updatedTeacherUsed), availablePool);
        teacher.setToken(updatedTeacherRemaining);
        teacher.setLastTokenResetAt(LocalDateTime.now());
        userRepository.save(teacher);

        // Add to student's daily bonus allowance
        grantDailyBonusFromSchoolPool(student, classroom, school, tokensToGrant, availablePool);
    }

    private void grantDailyBonusFromSchoolPool(
            User targetUser, Classroom classroom, School school, int tokensToGrant, int availablePool) {
        LocalDate today = LocalDate.now();
        if (targetUser.getLastTokenResetAt() == null || targetUser.getLastTokenResetAt().toLocalDate().isBefore(today)) {
            targetUser.setDailyTokensUsed(0);
            targetUser.setDailyBonusTokens(0);
        }

        int currentBonus = targetUser.getDailyBonusTokens() != null ? Math.max(0, targetUser.getDailyBonusTokens()) : 0;
        int updatedBonus = currentBonus + tokensToGrant;
        targetUser.setDailyBonusTokens(updatedBonus);

        int currentDisplayToken = targetUser.getToken() != null ? Math.max(0, targetUser.getToken()) : 0;
        int baseDailyLimit;
        if (targetUser.getRole() == UserRole.TEACHER) {
            baseDailyLimit = (school.getDefaultDailyTeacherQuota() != null && school.getDefaultDailyTeacherQuota() > 0)
                    ? school.getDefaultDailyTeacherQuota()
                    : DEFAULT_DAILY_TEACHER_QUOTA;
        } else if (classroom != null && classroom.getDailyTokenQuota() != null && classroom.getDailyTokenQuota() > 0) {
            baseDailyLimit = classroom.getDailyTokenQuota();
        } else if (school.getDefaultDailyStudentQuota() != null && school.getDefaultDailyStudentQuota() > 0) {
            baseDailyLimit = school.getDefaultDailyStudentQuota();
        } else {
            baseDailyLimit = DEFAULT_DAILY_STUDENT_QUOTA;
        }

        int usedToday = targetUser.getDailyTokensUsed() != null ? Math.max(0, targetUser.getDailyTokensUsed()) : 0;
        int recalculatedRemaining = Math.max(0, (baseDailyLimit + updatedBonus) - usedToday);
        int updatedDisplayToken = Math.max(currentDisplayToken + tokensToGrant, recalculatedRemaining);

        targetUser.setToken(Math.min(availablePool, updatedDisplayToken));
        targetUser.setLastTokenResetAt(LocalDateTime.now());
        userRepository.save(targetUser);
    }

    private UserPrincipal resolvePrincipal(UserPrincipal principal) {
        if (principal != null) {
            return principal;
        }
        String userId = SecurityUtils.getUserId();
        if (userId == null) {
            throw new UnauthorizedException("Vui lÃ²ng Ä‘Äƒng nháº­p Ä‘á»ƒ thá»±c hiá»‡n thao tÃ¡c");
        }
        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new UnauthorizedException("KhÃ´ng tÃ¬m tháº¥y thÃ´ng tin tÃ i khoáº£n ngÆ°á»i dÃ¹ng"));
        return new UserPrincipal(user);
    }

    private TokenBoostRequestResponse mapToResponse(TokenBoostRequest r) {
        User student = r.getStudent();
        User requester = r.getRequester() != null ? r.getRequester() : student;
        Classroom classroom = r.getClassroom();
        return TokenBoostRequestResponse.builder()
                .id(r.getId())
                .schoolId(r.getSchool().getId())
                .classroomId(classroom != null ? classroom.getId() : null)
                .className(classroom != null ? classroom.getClassName() : null)
                .studentUid(student != null ? student.getUid() : null)
                .studentName(student != null ? student.getFullName() : null)
                .studentCode(student != null ? student.getStudentCode() : null)
                .requesterUid(requester != null ? requester.getUid() : null)
                .requesterName(requester != null ? requester.getFullName() : null)
                .requesterRole(r.getRequesterRole() != null ? r.getRequesterRole() : UserRole.SCHOOL_STUDENT)
                .requestedTokens(r.getRequestedTokens())
                .approvedTokens(r.getApprovedTokens())
                .reason(r.getReason())
                .status(r.getStatus())
                .teacherReviewedByUid(r.getTeacherReviewedBy() != null ? r.getTeacherReviewedBy().getUid() : null)
                .teacherReviewedByName(r.getTeacherReviewedBy() != null ? r.getTeacherReviewedBy().getFullName() : null)
                .teacherReviewNote(r.getTeacherReviewNote())
                .teacherReviewedAt(r.getTeacherReviewedAt())
                .adminReviewedByUid(r.getAdminReviewedBy() != null ? r.getAdminReviewedBy().getUid() : null)
                .adminReviewedByName(r.getAdminReviewedBy() != null ? r.getAdminReviewedBy().getFullName() : null)
                .adminReviewNote(r.getAdminReviewNote())
                .adminReviewedAt(r.getAdminReviewedAt())
                .reviewedByUid(r.getReviewedBy() != null ? r.getReviewedBy().getUid() : null)
                .reviewedByName(r.getReviewedBy() != null ? r.getReviewedBy().getFullName() : null)
                .reviewNote(r.getReviewNote())
                .requestDate(r.getRequestDate())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
