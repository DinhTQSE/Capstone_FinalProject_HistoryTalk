package com.historytalk.service.school;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.CreateSchoolAdminRequest;
import com.historytalk.dto.school.SchoolAdminResponse;
import com.historytalk.dto.school.SchoolResponse;
import com.historytalk.dto.school.quote.ApproveB2bQuoteResponse;
import com.historytalk.dto.school.quote.B2bPlanCatalogResponse;
import com.historytalk.dto.school.quote.B2bQuoteCalculationResponse;
import com.historytalk.dto.school.quote.B2bQuoteResponse;
import com.historytalk.dto.school.quote.CalculateB2bQuoteRequest;
import com.historytalk.dto.school.quote.SubmitB2bQuoteRequest;
import com.historytalk.dto.school.quote.UpdateB2bQuoteStatusRequest;
import com.historytalk.entity.enums.B2bQuoteStatus;
import com.historytalk.entity.enums.EnterprisePackage;
import com.historytalk.entity.enums.SchoolStatus;
import com.historytalk.entity.school.B2bQuoteRequest;
import com.historytalk.entity.school.School;
import com.historytalk.exception.DataConflictException;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.school.B2bQuoteRequestRepository;
import com.historytalk.repository.school.SchoolRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class B2bQuoteServiceImpl implements B2bQuoteService {

    public static final double TOKEN_UNIT_PRICE_VND = 0.15;
    public static final int ACADEMIC_WEEKS_PER_YEAR = 35;
    public static final int ACADEMIC_MONTHS_PER_YEAR = 9;

    public static final int STUDENT_DAILY_TOKEN_LIMIT = 5_000;
    public static final int STUDENT_ACTIVE_DAYS_PER_WEEK = 4;
    public static final long STUDENT_TOKENS_PER_YEAR = 700_000L; // 5,000 * 4 * 35
    public static final long STUDENT_AI_TOKEN_COST_VND = 105_000L; // 700,000 * 0.15
    public static final long STUDENT_DB_AND_QUIZ_FEE_VND = 7_000L;
    public static final long STUDENT_TOTAL_ANNUAL_COST_VND = 112_000L; // 105,000 + 7,000

    public static final int TEACHER_DAILY_TOKEN_LIMIT = 5_000;
    public static final int TEACHER_ACTIVE_DAYS_PER_WEEK = 6;
    public static final long TEACHER_TOKENS_PER_YEAR = 1_050_000L; // 5,000 * 6 * 35
    public static final long TEACHER_AI_TOKEN_COST_VND = 157_500L; // 1,050,000 * 0.15
    public static final long TEACHER_LMS_AND_STORAGE_FEE_VND = 42_500L;
    public static final long TEACHER_TOTAL_ANNUAL_COST_VND = 200_000L; // 157,500 + 42,500

    private final B2bQuoteRequestRepository quoteRequestRepository;
    private final SchoolRepository schoolRepository;
    private final SchoolManagementService schoolManagementService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    @Transactional(readOnly = true)
    public B2bPlanCatalogResponse getB2bPlanCatalog() {
        List<B2bPlanCatalogResponse.DiscountTierItem> discountTiers = List.of(
                B2bPlanCatalogResponse.DiscountTierItem.builder()
                        .minStudents(1)
                        .maxStudents(599)
                        .discountPercent(0)
                        .description("Dưới 600 Học sinh (< 15 lớp): Giá gốc tiêu chuẩn (0% chiết khấu)")
                        .build(),
                B2bPlanCatalogResponse.DiscountTierItem.builder()
                        .minStudents(600)
                        .maxStudents(1199)
                        .discountPercent(10)
                        .description("Từ 600 đến 1.199 Học sinh (15–29 lớp - chuẩn gói Small): Giảm 10%")
                        .build(),
                B2bPlanCatalogResponse.DiscountTierItem.builder()
                        .minStudents(1200)
                        .maxStudents(1799)
                        .discountPercent(20)
                        .description("Từ 1.200 đến 1.799 Học sinh (30–44 lớp - chuẩn gói Medium): Giảm 20%")
                        .build(),
                B2bPlanCatalogResponse.DiscountTierItem.builder()
                        .minStudents(1800)
                        .maxStudents(null)
                        .discountPercent(30)
                        .description("Từ 1.800 Học sinh trở lên (45+ lớp - chuẩn gói Large): Giảm 30%")
                        .build()
        );

        List<B2bPlanCatalogResponse.FixedPlanItem> plans = List.of(
                buildFixedPlanItem(EnterprisePackage.ENTERPRISE_SMALL, "Gói Trường Nhỏ / THCS (Small)", false),
                buildFixedPlanItem(EnterprisePackage.ENTERPRISE_MEDIUM, "Gói Trường Tiêu Chuẩn (Medium)", true),
                buildFixedPlanItem(EnterprisePackage.ENTERPRISE_LARGE, "Gói Trường Lớn / Đô thị (Large)", false)
        );

        return B2bPlanCatalogResponse.builder()
                .tokenUnitPriceVnd(TOKEN_UNIT_PRICE_VND)
                .academicWeeksPerYear(ACADEMIC_WEEKS_PER_YEAR)
                .studentDailyTokenLimit(STUDENT_DAILY_TOKEN_LIMIT)
                .studentActiveDaysPerWeek(STUDENT_ACTIVE_DAYS_PER_WEEK)
                .studentTokensPerYear(STUDENT_TOKENS_PER_YEAR)
                .studentAiTokenCostVnd(STUDENT_AI_TOKEN_COST_VND)
                .studentDbAndQuizFeeVnd(STUDENT_DB_AND_QUIZ_FEE_VND)
                .studentTotalAnnualCostVnd(STUDENT_TOTAL_ANNUAL_COST_VND)
                .teacherDailyTokenLimit(TEACHER_DAILY_TOKEN_LIMIT)
                .teacherActiveDaysPerWeek(TEACHER_ACTIVE_DAYS_PER_WEEK)
                .teacherTokensPerYear(TEACHER_TOKENS_PER_YEAR)
                .teacherAiTokenCostVnd(TEACHER_AI_TOKEN_COST_VND)
                .teacherLmsAndStorageFeeVnd(TEACHER_LMS_AND_STORAGE_FEE_VND)
                .teacherTotalAnnualCostVnd(TEACHER_TOTAL_ANNUAL_COST_VND)
                .discountTiers(discountTiers)
                .plans(plans)
                .build();
    }

    @Override
    public B2bQuoteCalculationResponse calculateQuote(CalculateB2bQuoteRequest request) {
        if (request == null) {
            throw new InvalidRequestException("Quote calculation request must not be null");
        }
        int students = request.getNumberOfStudents() != null ? request.getNumberOfStudents() : 0;
        int teachers = request.getNumberOfTeachers() != null ? request.getNumberOfTeachers() : 0;

        if (students < 1 || students > 50_000) {
            throw new InvalidRequestException("Number of students must be between 1 and 50,000");
        }
        if (teachers < 1 || teachers > 5_000) {
            throw new InvalidRequestException("Number of teachers must be between 1 and 5,000");
        }

        EnterprisePackage resolvedPkg = resolvePackageType(request.getSelectedPackage(), students, teachers);

        long totalStudentTokens = students * STUDENT_TOKENS_PER_YEAR;
        long totalTeacherTokens = teachers * TEACHER_TOKENS_PER_YEAR;
        long totalTokenPool = totalStudentTokens + totalTeacherTokens;

        // Exact arithmetic: totalTokenPool * 0.15 VND = totalTokenPool * 15 / 100
        long aiTokenCostVnd = (totalTokenPool * 15L) / 100L;
        long studentDbCost = students * STUDENT_DB_AND_QUIZ_FEE_VND;
        long teacherLmsCost = teachers * TEACHER_LMS_AND_STORAGE_FEE_VND;
        long dbAndLmsCostVnd = studentDbCost + teacherLmsCost;

        long subtotalVnd = aiTokenCostVnd + dbAndLmsCostVnd;
        int discountPercent = resolveVolumeDiscountPercent(students);

        String appliedPromo = null;
        if (request.getPromoCode() != null && !request.getPromoCode().isBlank()) {
            String normalizedPromo = request.getPromoCode().trim().toUpperCase();
            if ("EDU2026".equals(normalizedPromo) || "HISTORYTALK5".equals(normalizedPromo)) {
                discountPercent = Math.min(50, discountPercent + 5);
                appliedPromo = normalizedPromo;
            }
        }

        if (request.getSubscriptionStartDate() != null && request.getSubscriptionEndDate() != null) {
            if (!request.getSubscriptionEndDate().isAfter(request.getSubscriptionStartDate())) {
                throw new InvalidRequestException("Ngày kết thúc năm học/gói phải sau ngày bắt đầu.");
            }
        }

        LocalDate subStart = request.getSubscriptionStartDate();
        LocalDate subEnd = request.getSubscriptionEndDate();
        if (subStart == null || subEnd == null) {
            LocalDate today = LocalDate.now();
            int academicStartYear = today.getMonthValue() >= 6 ? today.getYear() : today.getYear() - 1;
            subStart = LocalDate.of(academicStartYear, 9, 1);
            subEnd = LocalDate.of(academicStartYear + 1, 5, 31);
        }

        long discountAmountVnd = (subtotalVnd * discountPercent) / 100L;
        long finalPriceVnd = subtotalVnd - discountAmountVnd;
        long avgMonthlyPerStudent = students > 0 ? finalPriceVnd / ((long) students * ACADEMIC_MONTHS_PER_YEAR) : 0L;

        return B2bQuoteCalculationResponse.builder()
                .selectedPackage(resolvedPkg)
                .numberOfStudents(students)
                .numberOfTeachers(teachers)
                .studentDailyTokenLimit(STUDENT_DAILY_TOKEN_LIMIT)
                .teacherDailyTokenLimit(TEACHER_DAILY_TOKEN_LIMIT)
                .studentTokensPerYear(STUDENT_TOKENS_PER_YEAR)
                .teacherTokensPerYear(TEACHER_TOKENS_PER_YEAR)
                .totalStudentTokens(totalStudentTokens)
                .totalTeacherTokens(totalTeacherTokens)
                .totalTokenPool(totalTokenPool)
                .tokenUnitPriceVnd(TOKEN_UNIT_PRICE_VND)
                .aiTokenCostVnd(aiTokenCostVnd)
                .studentDbAndQuizFeeVnd(STUDENT_DB_AND_QUIZ_FEE_VND)
                .teacherLmsAndStorageFeeVnd(TEACHER_LMS_AND_STORAGE_FEE_VND)
                .dbAndLmsCostVnd(dbAndLmsCostVnd)
                .subtotalVnd(subtotalVnd)
                .discountPercent(discountPercent)
                .discountAmountVnd(discountAmountVnd)
                .finalPriceVnd(finalPriceVnd)
                .averageMonthlyCostPerStudentVnd(avgMonthlyPerStudent)
                .promoCodeApplied(appliedPromo)
                .subscriptionStartDate(subStart)
                .subscriptionEndDate(subEnd)
                .build();
    }

    @Override
    @Transactional
    public B2bQuoteResponse submitQuoteRequest(SubmitB2bQuoteRequest request) {
        LocalDate subStart = request.getSubscriptionStartDate();
        LocalDate subEnd = request.getSubscriptionEndDate();
        if (subStart != null && subEnd != null) {
            if (!subEnd.isAfter(subStart)) {
                throw new InvalidRequestException("Ngày kết thúc năm học/gói phải sau ngày bắt đầu.");
            }
        } else if (subStart != null || subEnd != null) {
            throw new InvalidRequestException("Vui lòng cung cấp đầy đủ cả ngày bắt đầu và ngày kết thúc chu kỳ gói.");
        } else {
            LocalDate today = LocalDate.now();
            int academicStartYear = today.getMonthValue() >= 6 ? today.getYear() : today.getYear() - 1;
            subStart = LocalDate.of(academicStartYear, 9, 1);
            subEnd = LocalDate.of(academicStartYear + 1, 5, 31);
        }

        B2bQuoteCalculationResponse calc = calculateQuote(CalculateB2bQuoteRequest.builder()
                .selectedPackage(request.getSelectedPackage())
                .numberOfStudents(request.getNumberOfStudents())
                .numberOfTeachers(request.getNumberOfTeachers())
                .promoCode(request.getPromoCode())
                .subscriptionStartDate(subStart)
                .subscriptionEndDate(subEnd)
                .build());

        String quoteCode = generateUniqueQuoteCode();

        B2bQuoteRequest entity = B2bQuoteRequest.builder()
                .quoteCode(quoteCode)
                .firstName(request.getFirstName().trim())
                .lastName(request.getLastName().trim())
                .contactEmail(request.getContactEmail().trim().toLowerCase())
                .contactPhone(request.getContactPhone().trim())
                .jobTitle(request.getJobTitle().trim())
                .schoolName(request.getSchoolName().trim())
                .schoolCode(request.getSchoolCode() != null && !request.getSchoolCode().isBlank()
                        ? request.getSchoolCode().trim().toUpperCase()
                        : null)
                .schoolAddress(request.getSchoolAddress() != null && !request.getSchoolAddress().isBlank()
                        ? request.getSchoolAddress().trim()
                        : null)
                .selectedPackage(calc.getSelectedPackage())
                .numberOfStudents(calc.getNumberOfStudents())
                .numberOfTeachers(calc.getNumberOfTeachers())
                .studentDailyTokenLimit(calc.getStudentDailyTokenLimit())
                .teacherDailyTokenLimit(calc.getTeacherDailyTokenLimit())
                .totalTokenPool(calc.getTotalTokenPool())
                .aiTokenCostVnd(calc.getAiTokenCostVnd())
                .dbAndLmsCostVnd(calc.getDbAndLmsCostVnd())
                .subtotalVnd(calc.getSubtotalVnd())
                .discountPercent(calc.getDiscountPercent())
                .discountAmountVnd(calc.getDiscountAmountVnd())
                .finalPriceVnd(calc.getFinalPriceVnd())
                .subscriptionStartDate(subStart)
                .subscriptionEndDate(subEnd)
                .promoCode(calc.getPromoCodeApplied())
                .message(request.getMessage() != null ? request.getMessage().trim() : null)
                .status(B2bQuoteStatus.PENDING)
                .build();

        B2bQuoteRequest saved = quoteRequestRepository.save(entity);
        log.info("Submitted new B2B quote request {} for school '{}' ({} students, {} teachers, finalPrice={} VND)",
                saved.getQuoteCode(), saved.getSchoolName(), saved.getNumberOfStudents(),
                saved.getNumberOfTeachers(), saved.getFinalPriceVnd());

        return mapToQuoteResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<B2bQuoteResponse> getQuoteRequests(
            B2bQuoteStatus status, String search, Pageable pageable) {
        String keyword = search != null ? search.trim() : "";
        Page<B2bQuoteRequest> page = quoteRequestRepository.searchQuotes(status, keyword, pageable);

        List<B2bQuoteResponse> content = page.getContent().stream()
                .map(this::mapToQuoteResponse)
                .toList();

        return PaginatedResponse.<B2bQuoteResponse>builder()
                .content(content)
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber() + 1)
                .pageSize(page.getSize())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public B2bQuoteResponse getQuoteRequestById(UUID quoteId) {
        B2bQuoteRequest quote = quoteRequestRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("B2B quote request not found with ID: " + quoteId));
        return mapToQuoteResponse(quote);
    }

    @Override
    @Transactional
    public B2bQuoteResponse updateQuoteStatus(
            UUID quoteId, UpdateB2bQuoteStatusRequest request, UUID adminUid) {
        B2bQuoteRequest quote = quoteRequestRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("B2B quote request not found with ID: " + quoteId));

        if (quote.getStatus() == B2bQuoteStatus.APPROVED) {
            throw new DataConflictException("Quote request has already been approved and cannot be modified");
        }
        if (request.getStatus() == B2bQuoteStatus.APPROVED) {
            throw new InvalidRequestException("Use the /approve endpoint to approve a quote and provision a school");
        }

        if (request.getCustomDiscountPercent() != null) {
            int customDiscount = request.getCustomDiscountPercent();
            long discountAmount = (quote.getSubtotalVnd() * customDiscount) / 100L;
            quote.setDiscountPercent(customDiscount);
            quote.setDiscountAmountVnd(discountAmount);
            quote.setFinalPriceVnd(quote.getSubtotalVnd() - discountAmount);
        }

        if (request.getSubscriptionStartDate() != null && request.getSubscriptionEndDate() != null) {
            if (!request.getSubscriptionEndDate().isAfter(request.getSubscriptionStartDate())) {
                throw new InvalidRequestException("Ngày kết thúc năm học/gói phải sau ngày bắt đầu.");
            }
            quote.setSubscriptionStartDate(request.getSubscriptionStartDate());
            quote.setSubscriptionEndDate(request.getSubscriptionEndDate());
        }

        quote.setStatus(request.getStatus());
        if (request.getAdminNote() != null) {
            quote.setAdminNote(request.getAdminNote().trim());
        }
        quote.setReviewedByUid(adminUid);
        quote.setReviewedAt(LocalDateTime.now());

        B2bQuoteRequest saved = quoteRequestRepository.save(quote);
        log.info("Updated B2B quote {} status to {} by admin {}", saved.getQuoteCode(), saved.getStatus(), adminUid);
        return mapToQuoteResponse(saved);
    }

    @Override
    @Transactional
    public ApproveB2bQuoteResponse approveQuoteAndProvisionSchool(UUID quoteId, UUID adminUid) {
        B2bQuoteRequest quote = quoteRequestRepository.findById(quoteId)
                .orElseThrow(() -> new ResourceNotFoundException("B2B quote request not found with ID: " + quoteId));

        if (quote.getStatus() == B2bQuoteStatus.APPROVED) {
            throw new DataConflictException("Quote request " + quote.getQuoteCode() + " has already been approved");
        }
        if (quote.getStatus() == B2bQuoteStatus.REJECTED) {
            throw new InvalidRequestException("Cannot approve a rejected quote request");
        }

        SchoolResponse createdSchool = schoolManagementService.createSchool(
                com.historytalk.dto.school.CreateSchoolRequest.builder()
                        .name(quote.getSchoolName())
                        .schoolCode(quote.getSchoolCode())
                        .address(quote.getSchoolAddress())
                        .contactEmail(quote.getContactEmail())
                        .contactPhone(quote.getContactPhone())
                        .packageType(quote.getSelectedPackage())
                        .subscriptionStartDate(quote.getSubscriptionStartDate())
                        .subscriptionEndDate(quote.getSubscriptionEndDate())
                        .localHistoryPolicyAccepted(true)
                        .build());

        // Override the school's token pool, daily limits, and max student/teacher caps with the exact quote values
        School schoolEntity = schoolRepository.findById(createdSchool.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Provisioned school not found"));
        int exactPool = (int) Math.min(Integer.MAX_VALUE, quote.getTotalTokenPool());
        schoolEntity.setTotalSchoolTokenQuota(exactPool);
        schoolEntity.setUnallocatedTokenQuota(exactPool);
        schoolEntity.setDefaultDailyStudentQuota(quote.getStudentDailyTokenLimit());
        schoolEntity.setDefaultDailyTeacherQuota(quote.getTeacherDailyTokenLimit());
        schoolEntity.setMaxStudents(quote.getNumberOfStudents());
        schoolEntity.setMaxTeachers(quote.getNumberOfTeachers());
        if (quote.getSubscriptionStartDate() != null) {
            schoolEntity.setSubscriptionStartDate(quote.getSubscriptionStartDate());
        }
        if (quote.getSubscriptionEndDate() != null) {
            schoolEntity.setSubscriptionEndDate(quote.getSubscriptionEndDate());
        }
        schoolEntity.setStatus(SchoolStatus.ACTIVE);
        schoolRepository.save(schoolEntity);

        createdSchool.setTotalSchoolTokenQuota(exactPool);
        createdSchool.setUnallocatedTokenQuota(exactPool);
        createdSchool.setDefaultDailyStudentQuota(quote.getStudentDailyTokenLimit());
        createdSchool.setDefaultDailyTeacherQuota(quote.getTeacherDailyTokenLimit());
        createdSchool.setRecommendedStudents(quote.getNumberOfStudents());
        createdSchool.setSubscriptionStartDate(schoolEntity.getSubscriptionStartDate());
        createdSchool.setSubscriptionEndDate(schoolEntity.getSubscriptionEndDate());

        String fullName = (quote.getLastName() + " " + quote.getFirstName()).trim();
        SchoolAdminResponse schoolAdmin = schoolManagementService.createSchoolAdmin(
                CreateSchoolAdminRequest.builder()
                        .schoolId(createdSchool.getId())
                        .fullName(fullName)
                        .email(quote.getContactEmail())
                        .phoneNumber(quote.getContactPhone())
                        .build());

        quote.setStatus(B2bQuoteStatus.APPROVED);
        quote.setReviewedByUid(adminUid);
        quote.setReviewedAt(LocalDateTime.now());
        quote.setCreatedSchoolId(createdSchool.getId());
        quote.setCreatedSchoolAdminUid(schoolAdmin.getUid());
        B2bQuoteRequest savedQuote = quoteRequestRepository.save(quote);

        log.info("Approved B2B quote {} -> provisioned School {} ({}) with {} tokens and School Admin {}",
                savedQuote.getQuoteCode(), createdSchool.getName(), createdSchool.getSchoolCode(),
                exactPool, schoolAdmin.getUserName());

        return ApproveB2bQuoteResponse.builder()
                .quote(mapToQuoteResponse(savedQuote))
                .school(createdSchool)
                .schoolAdmin(schoolAdmin)
                .build();
    }

    public static int resolveVolumeDiscountPercent(int students) {
        if (students >= 1_800) {
            return 30;
        }
        if (students >= 1_200) {
            return 20;
        }
        if (students >= 600) {
            return 10;
        }
        return 0;
    }

    private EnterprisePackage resolvePackageType(
            EnterprisePackage requestedPackage, int students, int teachers) {
        if (requestedPackage != null && requestedPackage != EnterprisePackage.ENTERPRISE_CUSTOM) {
            if (students == requestedPackage.getRecommendedStudents()
                    && teachers == requestedPackage.getRecommendedTeachers()) {
                return requestedPackage;
            }
        }
        if (students == EnterprisePackage.ENTERPRISE_SMALL.getRecommendedStudents()
                && teachers == EnterprisePackage.ENTERPRISE_SMALL.getRecommendedTeachers()) {
            return EnterprisePackage.ENTERPRISE_SMALL;
        }
        if (students == EnterprisePackage.ENTERPRISE_MEDIUM.getRecommendedStudents()
                && teachers == EnterprisePackage.ENTERPRISE_MEDIUM.getRecommendedTeachers()) {
            return EnterprisePackage.ENTERPRISE_MEDIUM;
        }
        if (students == EnterprisePackage.ENTERPRISE_LARGE.getRecommendedStudents()
                && teachers == EnterprisePackage.ENTERPRISE_LARGE.getRecommendedTeachers()) {
            return EnterprisePackage.ENTERPRISE_LARGE;
        }
        return requestedPackage != null ? requestedPackage : EnterprisePackage.ENTERPRISE_CUSTOM;
    }

    private B2bPlanCatalogResponse.FixedPlanItem buildFixedPlanItem(
            EnterprisePackage pkg, String displayName, boolean popular) {
        B2bQuoteCalculationResponse calc = calculateQuote(CalculateB2bQuoteRequest.builder()
                .selectedPackage(pkg)
                .numberOfStudents(pkg.getRecommendedStudents())
                .numberOfTeachers(pkg.getRecommendedTeachers())
                .build());

        List<String> highlights = List.of(
                "Tối đa " + pkg.getRecommendedStudents() + " Học sinh & " + pkg.getRecommendedTeachers() + " Giáo viên",
                "Quỹ Token chung: " + String.format("%,d", calc.getTotalTokenPool()) + " tokens / năm học",
                "Hạn mức ngày: 5.000 tokens/ngày cho cả Học sinh & Giáo viên",
                "Kho nội dung Lịch sử Địa phương riêng biệt (PDF, Video, Nhân vật)",
                "Chế độ Thi Vấn Đáp (Exam Mode) & Quiz không giới hạn, lưu điểm theo lớp"
        );

        return B2bPlanCatalogResponse.FixedPlanItem.builder()
                .packageType(pkg)
                .displayName(displayName)
                .maxStudents(pkg.getRecommendedStudents())
                .maxTeachers(pkg.getRecommendedTeachers())
                .studentDailyTokenLimit(STUDENT_DAILY_TOKEN_LIMIT)
                .teacherDailyTokenLimit(TEACHER_DAILY_TOKEN_LIMIT)
                .totalTokenPool(calc.getTotalTokenPool())
                .aiTokenCostVnd(calc.getAiTokenCostVnd())
                .dbAndLmsCostVnd(calc.getDbAndLmsCostVnd())
                .subtotalVnd(calc.getSubtotalVnd())
                .discountPercent(calc.getDiscountPercent())
                .discountAmountVnd(calc.getDiscountAmountVnd())
                .finalPriceVnd(calc.getFinalPriceVnd())
                .averageMonthlyCostPerStudentVnd(calc.getAverageMonthlyCostPerStudentVnd())
                .popular(popular)
                .highlights(highlights)
                .build();
    }

    private String generateUniqueQuoteCode() {
        int year = LocalDate.now().getYear();
        String candidate;
        do {
            int rand = 1000 + secureRandom.nextInt(9000);
            candidate = String.format("QT-%d-%04d", year, rand);
        } while (quoteRequestRepository.existsByQuoteCodeIgnoreCase(candidate));
        return candidate;
    }

    private B2bQuoteResponse mapToQuoteResponse(B2bQuoteRequest q) {
        String fullName = ((q.getLastName() != null ? q.getLastName() : "") + " "
                + (q.getFirstName() != null ? q.getFirstName() : "")).trim();
        return B2bQuoteResponse.builder()
                .id(q.getId())
                .quoteCode(q.getQuoteCode())
                .firstName(q.getFirstName())
                .lastName(q.getLastName())
                .fullName(fullName)
                .contactEmail(q.getContactEmail())
                .contactPhone(q.getContactPhone())
                .jobTitle(q.getJobTitle())
                .schoolName(q.getSchoolName())
                .schoolCode(q.getSchoolCode())
                .schoolAddress(q.getSchoolAddress())
                .selectedPackage(q.getSelectedPackage())
                .numberOfStudents(q.getNumberOfStudents())
                .numberOfTeachers(q.getNumberOfTeachers())
                .studentDailyTokenLimit(q.getStudentDailyTokenLimit())
                .teacherDailyTokenLimit(q.getTeacherDailyTokenLimit())
                .totalTokenPool(q.getTotalTokenPool())
                .aiTokenCostVnd(q.getAiTokenCostVnd())
                .dbAndLmsCostVnd(q.getDbAndLmsCostVnd())
                .subtotalVnd(q.getSubtotalVnd())
                .discountPercent(q.getDiscountPercent())
                .discountAmountVnd(q.getDiscountAmountVnd())
                .finalPriceVnd(q.getFinalPriceVnd())
                .subscriptionStartDate(q.getSubscriptionStartDate())
                .subscriptionEndDate(q.getSubscriptionEndDate())
                .promoCode(q.getPromoCode())
                .message(q.getMessage())
                .status(q.getStatus())
                .adminNote(q.getAdminNote())
                .reviewedByUid(q.getReviewedByUid())
                .reviewedAt(q.getReviewedAt())
                .createdSchoolId(q.getCreatedSchoolId())
                .createdSchoolAdminUid(q.getCreatedSchoolAdminUid())
                .createdAt(q.getCreatedAt() != null ? q.getCreatedAt() : LocalDateTime.now())
                .updatedAt(q.getUpdatedAt())
                .build();
    }
}
