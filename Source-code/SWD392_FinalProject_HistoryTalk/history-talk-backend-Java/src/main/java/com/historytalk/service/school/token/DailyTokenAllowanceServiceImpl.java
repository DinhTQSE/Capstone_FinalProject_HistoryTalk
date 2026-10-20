package com.historytalk.service.school.token;

import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.AccountStatus;
import com.historytalk.entity.enums.SchoolStatus;
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.repository.UserRepository;
import com.historytalk.repository.classroom.ClassStudentRepository;
import com.historytalk.repository.school.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DailyTokenAllowanceServiceImpl implements DailyTokenAllowanceService {

    private static final int DEFAULT_DAILY_STUDENT_QUOTA = 1000;
    private static final int DEFAULT_DAILY_TEACHER_QUOTA = 10000;

    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final ClassStudentRepository classStudentRepository;

    @Override
    @Transactional
    public int verifyAndPrepareDailyAllowance(User user) {
        if (user.getDeletedAt() != null || user.getAccountStatus() == AccountStatus.DEACTIVATED) {
            throw new AccessDeniedException("ACCOUNT_DEACTIVATED: Tài khoản đã bị vô hiệu hóa do đã ra trường hoặc chuyển trường.");
        }

        School school = user.getSchool();
        if (school == null) {
            throw new AccessDeniedException("Tài khoản không thuộc trường học nào.");
        }

        verifySchoolSubscriptionActive(school);

        LocalDate today = LocalDate.now();
        boolean needsDailyReset = user.getLastTokenResetAt() == null
                || user.getLastTokenResetAt().toLocalDate().isBefore(today);

        if (needsDailyReset) {
            // Pure Shared Pool: All school tokens remain in school.unallocatedTokenQuota until spent.
            // On a new day, simply reset the user's daily usage counter and daily bonus allowance to 0.
            user.setDailyTokensUsed(0);
            user.setDailyBonusTokens(0);
            user.setLastTokenResetAt(LocalDateTime.now());
        }

        int baseDailyLimit = resolveUserDailyLimit(user, school);
        int bonusToday = user.getDailyBonusTokens() != null ? Math.max(0, user.getDailyBonusTokens()) : 0;
        int usedToday = user.getDailyTokensUsed() != null ? Math.max(0, user.getDailyTokensUsed()) : 0;
        int effectiveDailyLimit = baseDailyLimit + bonusToday;

        int remainingDailyLimit = Math.max(0, effectiveDailyLimit - usedToday);
        int schoolPool = school.getUnallocatedTokenQuota() != null ? Math.max(0, school.getUnallocatedTokenQuota()) : 0;
        int effectiveAvailableToday = Math.min(remainingDailyLimit, schoolPool);

        // Sync user.token with effectiveAvailableToday for UI display convenience (no tokens removed from schoolPool upfront)
        user.setToken(effectiveAvailableToday);
        if (needsDailyReset) {
            userRepository.save(user);
        }

        if (effectiveAvailableToday <= 0) {
            if (user.getRole() == UserRole.TEACHER) {
                throw new AccessDeniedException(
                        "AI_QUOTA_EXCEEDED: Bạn đã sử dụng hết hạn mức Token trong ngày của Giáo viên (đã bao gồm phần cấp cho học sinh). " +
                        "Bạn có thể tạo Ticket gửi Quản trị viên Trường (School Admin) để được cấp thêm hạn mức.");
            }
            throw new AccessDeniedException(
                    "AI_QUOTA_EXCEEDED: Bạn đã hết hạn mức Token AI trong ngày (hoặc Quỹ Token chung của trường đã hết). " +
                    "Các tính năng làm bài Quiz, đọc tài liệu và nộp bài tập vẫn sử dụng bình thường. " +
                    "Bạn có thể bấm 'Xin thêm Token' để giáo viên phụ trách lớp cấp thêm từ quỹ giáo viên.");
        }

        return effectiveAvailableToday;
    }

    @Override
    @Transactional
    public int recordStudentTokenUsage(User user, int tokensUsed) {
        if (tokensUsed <= 0 || user == null || user.getSchool() == null) {
            return user != null && user.getToken() != null ? user.getToken() : 0;
        }

        School school = user.getSchool();
        int currentPool = school.getUnallocatedTokenQuota() != null ? Math.max(0, school.getUnallocatedTokenQuota()) : 0;
        int updatedPool = Math.max(0, currentPool - tokensUsed);
        school.setUnallocatedTokenQuota(updatedPool);

        int usedToday = user.getDailyTokensUsed() != null ? Math.max(0, user.getDailyTokensUsed()) : 0;
        int updatedUsedToday = usedToday + tokensUsed;
        user.setDailyTokensUsed(updatedUsedToday);

        int baseDailyLimit = resolveUserDailyLimit(user, school);
        int bonusToday = user.getDailyBonusTokens() != null ? Math.max(0, user.getDailyBonusTokens()) : 0;
        int remainingDailyLimit = Math.max(0, (baseDailyLimit + bonusToday) - updatedUsedToday);
        int effectiveRemaining = Math.min(remainingDailyLimit, updatedPool);

        user.setToken(effectiveRemaining);
        if (user.getLastTokenResetAt() == null) {
            user.setLastTokenResetAt(LocalDateTime.now());
        }

        schoolRepository.save(school);
        userRepository.save(user);

        log.info("Recorded {} tokens used by {} ({}) directly from school {} pool. UsedToday={}/{}, RemainingPool={}",
                tokensUsed, user.getUserName(), user.getRole(), school.getSchoolCode(),
                updatedUsedToday, (baseDailyLimit + bonusToday), updatedPool);

        return effectiveRemaining;
    }

    @Override
    @Transactional
    public void verifySchoolSubscriptionForStaff(User staffUser) {
        if (staffUser.getDeletedAt() != null || staffUser.getAccountStatus() == AccountStatus.DEACTIVATED) {
            throw new AccessDeniedException("ACCOUNT_DEACTIVATED: Tài khoản đã bị vô hiệu hóa.");
        }
        School school = staffUser.getSchool();
        if (school != null) {
            verifySchoolSubscriptionActive(school);
        }
    }

    public void verifySchoolSubscriptionActive(School school) {
        if (school.getStatus() == SchoolStatus.EXPIRED
                || school.getStatus() == SchoolStatus.SUSPENDED
                || school.getStatus() == SchoolStatus.INACTIVE) {
            throw new AccessDeniedException(
                    "SCHOOL_SUBSCRIPTION_EXPIRED: Gói đăng ký của trường hiện đang bị khóa hoặc đã hết hạn. " +
                    "Toàn bộ nền tảng trường học tạm ngưng cho đến khi gia hạn năm học mới.");
        }

        LocalDate today = LocalDate.now();
        if (school.getSubscriptionEndDate() != null && today.isAfter(school.getSubscriptionEndDate())) {
            school.setStatus(SchoolStatus.EXPIRED);
            schoolRepository.save(school);
            throw new AccessDeniedException(
                    "SCHOOL_SUBSCRIPTION_EXPIRED: Gói năm học của trường đã hết hạn vào ngày "
                            + school.getSubscriptionEndDate() + ". Vui lòng gia hạn để tiếp tục sử dụng nền tảng.");
        }

        if (school.getSubscriptionStartDate() != null && today.isBefore(school.getSubscriptionStartDate())) {
            throw new AccessDeniedException(
                    "SCHOOL_SUBSCRIPTION_NOT_STARTED: Gói năm học của trường sẽ bắt đầu kích hoạt từ ngày "
                            + school.getSubscriptionStartDate() + " (01/09).");
        }
    }

    private int resolveUserDailyLimit(User user, School school) {
        if (user.getRole() == UserRole.TEACHER) {
            if (school.getDefaultDailyTeacherQuota() != null && school.getDefaultDailyTeacherQuota() > 0) {
                return school.getDefaultDailyTeacherQuota();
            }
            return DEFAULT_DAILY_TEACHER_QUOTA;
        }
        List<Classroom> activeClassrooms = classStudentRepository.findActiveClassroomsByStudentUid(user.getUid());
        for (Classroom c : activeClassrooms) {
            if (c.getDailyTokenQuota() != null && c.getDailyTokenQuota() > 0) {
                return c.getDailyTokenQuota();
            }
        }
        if (school.getDefaultDailyStudentQuota() != null && school.getDefaultDailyStudentQuota() > 0) {
            return school.getDefaultDailyStudentQuota();
        }
        return DEFAULT_DAILY_STUDENT_QUOTA;
    }
}
