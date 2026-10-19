package com.historytalk.security;

import com.historytalk.entity.enums.AccountStatus;
import com.historytalk.entity.enums.SchoolStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.school.SchoolRepository;
import com.historytalk.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Enforces two-level B2B school locking:
 * 1. Deactivated student account (graduated / transferred) -> Blocks all access (403 ACCOUNT_DEACTIVATED).
 * 2. Expired school subscription (outside 01/09 - 31/05 or status EXPIRED/SUSPENDED) -> Locks the entire platform for the school (403 SCHOOL_SUBSCRIPTION_EXPIRED).
 * 3. Active school subscription with 0 tokens -> Allows all non-AI endpoints (Quizzes, Assignments, Local Content, Dashboards); only AI Chat is blocked by DailyTokenAllowanceService.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchoolSubscriptionGuardInterceptor implements HandlerInterceptor {

    private final UserRepository userRepository;
    private final SchoolRepository schoolRepository;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String userIdStr = SecurityUtils.getUserId();
        if (userIdStr == null || userIdStr.isBlank()) {
            return true;
        }

        UUID uid;
        try {
            uid = UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            return true;
        }

        User user = userRepository.findById(uid).orElse(null);
        if (user == null) {
            return true;
        }

        if (user.getDeletedAt() != null || user.getAccountStatus() == AccountStatus.DEACTIVATED) {
            throw new AccessDeniedException("ACCOUNT_DEACTIVATED: Tài khoản đã bị vô hiệu hóa do đã ra trường hoặc chuyển trường.");
        }

        UserRole role = user.getRole();
        if (role == UserRole.SCHOOL_ADMIN || role == UserRole.TEACHER || role == UserRole.SCHOOL_STUDENT) {
            School school = user.getSchool();
            if (school != null) {
                LocalDate today = LocalDate.now();
                if (school.getSubscriptionEndDate() != null && today.isAfter(school.getSubscriptionEndDate())) {
                    if (school.getStatus() != SchoolStatus.EXPIRED) {
                        school.setStatus(SchoolStatus.EXPIRED);
                        schoolRepository.save(school);
                    }
                    throw new AccessDeniedException(
                            "SCHOOL_SUBSCRIPTION_EXPIRED: Gói năm học của trường đã hết hạn vào ngày "
                                    + school.getSubscriptionEndDate() + ". Toàn bộ nền tảng của trường đã bị khóa cho đến khi gia hạn.");
                }

                if (school.getStatus() == SchoolStatus.EXPIRED
                        || school.getStatus() == SchoolStatus.SUSPENDED
                        || school.getStatus() == SchoolStatus.INACTIVE) {
                    throw new AccessDeniedException(
                            "SCHOOL_SUBSCRIPTION_EXPIRED: Gói đăng ký của trường đã hết hạn hoặc đang bị khóa. " +
                            "Toàn bộ nền tảng của trường tạm ngưng hoạt động.");
                }

                if (school.getSubscriptionStartDate() != null && today.isBefore(school.getSubscriptionStartDate())) {
                    throw new AccessDeniedException(
                            "SCHOOL_SUBSCRIPTION_NOT_STARTED: Gói năm học của trường sẽ bắt đầu mở từ ngày "
                                    + school.getSubscriptionStartDate() + " (01/09).");
                }
            }
        }

        return true;
    }
}
