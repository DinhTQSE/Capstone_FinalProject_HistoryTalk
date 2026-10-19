package com.historytalk.service.school.token;

import com.historytalk.entity.classroom.Classroom;
import com.historytalk.entity.enums.AccountStatus;
import com.historytalk.entity.enums.SchoolStatus;
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

    private static final int DEFAULT_DAILY_QUOTA = 1000;

    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final ClassStudentRepository classStudentRepository;

    @Override
    @Transactional
    public int verifyAndPrepareDailyAllowance(User student) {
        if (student.getDeletedAt() != null || student.getAccountStatus() == AccountStatus.DEACTIVATED) {
            throw new AccessDeniedException("ACCOUNT_DEACTIVATED: Tài khoản học sinh đã bị vô hiệu hóa do đã ra trường hoặc chuyển trường.");
        }

        School school = student.getSchool();
        if (school == null) {
            throw new AccessDeniedException("Tài khoản học sinh không thuộc trường học nào.");
        }

        verifySchoolSubscriptionActive(school);

        LocalDate today = LocalDate.now();
        boolean needsDailyReset = student.getLastTokenResetAt() == null
                || student.getLastTokenResetAt().toLocalDate().isBefore(today);

        if (needsDailyReset) {
            // 1. Refund any unused tokens from previous day back to School Shared Pool (prevent stranded tokens)
            int unusedPreviousTokens = student.getToken() != null ? Math.max(0, student.getToken()) : 0;
            int currentPool = school.getUnallocatedTokenQuota() != null ? school.getUnallocatedTokenQuota() : 0;
            int updatedPool = currentPool + unusedPreviousTokens;

            // 2. Resolve daily limit (Classroom override -> School default -> 1000)
            int dailyLimit = resolveStudentDailyLimit(student, school);

            // 3. Allocate today's allowance from School Shared Pool
            int allocatedToday = Math.min(dailyLimit, Math.max(0, updatedPool));
            school.setUnallocatedTokenQuota(updatedPool - allocatedToday);
            student.setToken(allocatedToday);
            student.setLastTokenResetAt(LocalDateTime.now());

            schoolRepository.save(school);
            userRepository.save(student);

            log.info("Daily token reset for student {} (school {}): refunded={}, allocatedToday={}, remainingPool={}",
                    student.getUserName(), school.getSchoolCode(), unusedPreviousTokens, allocatedToday, school.getUnallocatedTokenQuota());
        }

        int currentTokens = student.getToken() != null ? student.getToken() : 0;
        if (currentTokens <= 0) {
            throw new AccessDeniedException(
                    "AI_QUOTA_EXCEEDED: Bạn đã hết hạn mức Token AI trong ngày (hoặc Quỹ Token chung của trường đã hết). " +
                    "Các tính năng làm bài Quiz, đọc tài liệu và nộp bài tập vẫn sử dụng bình thường. " +
                    "Bạn có thể bấm 'Xin thêm Token' để giáo viên phụ trách lớp duyệt cấp thêm.");
        }

        return currentTokens;
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

    private int resolveStudentDailyLimit(User student, School school) {
        List<Classroom> activeClassrooms = classStudentRepository.findActiveClassroomsByStudentUid(student.getUid());
        for (Classroom c : activeClassrooms) {
            if (c.getDailyTokenQuota() != null && c.getDailyTokenQuota() > 0) {
                return c.getDailyTokenQuota();
            }
        }
        if (school.getDefaultDailyStudentQuota() != null && school.getDefaultDailyStudentQuota() > 0) {
            return school.getDefaultDailyStudentQuota();
        }
        return DEFAULT_DAILY_QUOTA;
    }
}
