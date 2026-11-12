package com.historytalk.service.school;

import com.historytalk.dto.school.CreateSchoolAdminRequest;
import com.historytalk.dto.school.CreateSchoolRequest;
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
import com.historytalk.entity.enums.UserRole;
import com.historytalk.entity.school.B2bQuoteRequest;
import com.historytalk.entity.school.School;
import com.historytalk.exception.DataConflictException;
import com.historytalk.repository.school.B2bQuoteRequestRepository;
import com.historytalk.repository.school.SchoolRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class B2bQuoteServiceImplTest {

    @Mock
    private B2bQuoteRequestRepository quoteRequestRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private SchoolManagementService schoolManagementService;

    @InjectMocks
    private B2bQuoteServiceImpl b2bQuoteService;

    @Test
    @DisplayName("UT-B2B-01: Get plan catalog returns 3 fixed plans and 4 volume discount tiers")
    void getB2bPlanCatalog_ShouldReturnThreeFixedPlansAndFourDiscountTiers() {
        B2bPlanCatalogResponse catalog = b2bQuoteService.getB2bPlanCatalog();

        assertThat(catalog).isNotNull();
        assertThat(catalog.getAcademicWeeksPerYear()).isEqualTo(35);
        assertThat(catalog.getTokenUnitPriceVnd()).isEqualTo(0.15);
        assertThat(catalog.getStudentTotalAnnualCostVnd()).isEqualTo(112_000L);
        assertThat(catalog.getTeacherTotalAnnualCostVnd()).isEqualTo(200_000L);
        assertThat(catalog.getPlans()).hasSize(3);
        assertThat(catalog.getDiscountTiers()).hasSize(4);

        assertThat(catalog.getPlans().get(0).getPackageType()).isEqualTo(EnterprisePackage.ENTERPRISE_SMALL);
        assertThat(catalog.getPlans().get(0).getFinalPriceVnd()).isEqualTo(63_180_000L);
        assertThat(catalog.getPlans().get(1).getPackageType()).isEqualTo(EnterprisePackage.ENTERPRISE_MEDIUM);
        assertThat(catalog.getPlans().get(1).getFinalPriceVnd()).isEqualTo(131_040_000L);
        assertThat(catalog.getPlans().get(2).getPackageType()).isEqualTo(EnterprisePackage.ENTERPRISE_LARGE);
        assertThat(catalog.getPlans().get(2).getFinalPriceVnd()).isEqualTo(163_800_000L);
    }

    @Test
    @DisplayName("UT-B2B-02: Calculate quote for ENTERPRISE_SMALL (600 students, 15 teachers -> 435,750,000 tokens, 63,180,000 VND)")
    void calculateQuote_FixedEnterpriseSmall_ShouldReturn600Students15TeachersAnd63180000Vnd() {
        CalculateB2bQuoteRequest request = CalculateB2bQuoteRequest.builder()
                .selectedPackage(EnterprisePackage.ENTERPRISE_SMALL)
                .numberOfStudents(600)
                .numberOfTeachers(15)
                .build();

        B2bQuoteCalculationResponse res = b2bQuoteService.calculateQuote(request);

        assertThat(res.getSelectedPackage()).isEqualTo(EnterprisePackage.ENTERPRISE_SMALL);
        assertThat(res.getNumberOfStudents()).isEqualTo(600);
        assertThat(res.getNumberOfTeachers()).isEqualTo(15);
        assertThat(res.getTotalTokenPool()).isEqualTo(435_750_000L);
        assertThat(res.getAiTokenCostVnd()).isEqualTo(65_362_500L);
        assertThat(res.getDbAndLmsCostVnd()).isEqualTo(4_837_500L);
        assertThat(res.getSubtotalVnd()).isEqualTo(70_200_000L);
        assertThat(res.getDiscountPercent()).isEqualTo(10);
        assertThat(res.getDiscountAmountVnd()).isEqualTo(7_020_000L);
        assertThat(res.getFinalPriceVnd()).isEqualTo(63_180_000L);
    }

    @Test
    @DisplayName("UT-B2B-03: Calculate quote for ENTERPRISE_MEDIUM (1,400 students, 35 teachers -> 1,016,750,000 tokens, 131,040,000 VND)")
    void calculateQuote_FixedEnterpriseMedium_ShouldReturn1400Students35TeachersAnd131040000Vnd() {
        CalculateB2bQuoteRequest request = CalculateB2bQuoteRequest.builder()
                .selectedPackage(EnterprisePackage.ENTERPRISE_MEDIUM)
                .numberOfStudents(1400)
                .numberOfTeachers(35)
                .build();

        B2bQuoteCalculationResponse res = b2bQuoteService.calculateQuote(request);

        assertThat(res.getSelectedPackage()).isEqualTo(EnterprisePackage.ENTERPRISE_MEDIUM);
        assertThat(res.getNumberOfStudents()).isEqualTo(1400);
        assertThat(res.getNumberOfTeachers()).isEqualTo(35);
        assertThat(res.getTotalTokenPool()).isEqualTo(1_016_750_000L);
        assertThat(res.getAiTokenCostVnd()).isEqualTo(152_512_500L);
        assertThat(res.getDbAndLmsCostVnd()).isEqualTo(11_287_500L);
        assertThat(res.getSubtotalVnd()).isEqualTo(163_800_000L);
        assertThat(res.getDiscountPercent()).isEqualTo(20);
        assertThat(res.getDiscountAmountVnd()).isEqualTo(32_760_000L);
        assertThat(res.getFinalPriceVnd()).isEqualTo(131_040_000L);
    }

    @Test
    @DisplayName("UT-B2B-04: Calculate quote for ENTERPRISE_LARGE (2,000 students, 50 teachers -> 1,452,500,000 tokens, 163,800,000 VND)")
    void calculateQuote_FixedEnterpriseLarge_ShouldReturn2000Students50TeachersAnd163800000Vnd() {
        CalculateB2bQuoteRequest request = CalculateB2bQuoteRequest.builder()
                .selectedPackage(EnterprisePackage.ENTERPRISE_LARGE)
                .numberOfStudents(2000)
                .numberOfTeachers(50)
                .build();

        B2bQuoteCalculationResponse res = b2bQuoteService.calculateQuote(request);

        assertThat(res.getSelectedPackage()).isEqualTo(EnterprisePackage.ENTERPRISE_LARGE);
        assertThat(res.getNumberOfStudents()).isEqualTo(2000);
        assertThat(res.getNumberOfTeachers()).isEqualTo(50);
        assertThat(res.getTotalTokenPool()).isEqualTo(1_452_500_000L);
        assertThat(res.getAiTokenCostVnd()).isEqualTo(217_875_000L);
        assertThat(res.getDbAndLmsCostVnd()).isEqualTo(16_125_000L);
        assertThat(res.getSubtotalVnd()).isEqualTo(234_000_000L);
        assertThat(res.getDiscountPercent()).isEqualTo(30);
        assertThat(res.getDiscountAmountVnd()).isEqualTo(70_200_000L);
        assertThat(res.getFinalPriceVnd()).isEqualTo(163_800_000L);
    }

    @Test
    @DisplayName("UT-B2B-05: Custom plan under 600 students applies 0% discount (300 students, 10 teachers -> 35,600,000 VND)")
    void calculateQuote_CustomTier0PercentDiscount_Under600Students() {
        CalculateB2bQuoteRequest request = CalculateB2bQuoteRequest.builder()
                .selectedPackage(EnterprisePackage.ENTERPRISE_CUSTOM)
                .numberOfStudents(300)
                .numberOfTeachers(10)
                .build();

        B2bQuoteCalculationResponse res = b2bQuoteService.calculateQuote(request);

        assertThat(res.getTotalTokenPool()).isEqualTo(220_500_000L);
        assertThat(res.getSubtotalVnd()).isEqualTo(35_600_000L);
        assertThat(res.getDiscountPercent()).isEqualTo(0);
        assertThat(res.getDiscountAmountVnd()).isEqualTo(0L);
        assertThat(res.getFinalPriceVnd()).isEqualTo(35_600_000L);
    }

    @Test
    @DisplayName("UT-B2B-06: Custom plan with 850 students and 22 teachers applies 10% discount (89,640,000 VND)")
    void calculateQuote_CustomTier10PercentDiscount_600To1199Students() {
        CalculateB2bQuoteRequest request = CalculateB2bQuoteRequest.builder()
                .selectedPackage(EnterprisePackage.ENTERPRISE_CUSTOM)
                .numberOfStudents(850)
                .numberOfTeachers(22)
                .build();

        B2bQuoteCalculationResponse res = b2bQuoteService.calculateQuote(request);

        assertThat(res.getTotalTokenPool()).isEqualTo(618_100_000L);
        assertThat(res.getAiTokenCostVnd()).isEqualTo(92_715_000L);
        assertThat(res.getDbAndLmsCostVnd()).isEqualTo(6_885_000L);
        assertThat(res.getSubtotalVnd()).isEqualTo(99_600_000L);
        assertThat(res.getDiscountPercent()).isEqualTo(10);
        assertThat(res.getDiscountAmountVnd()).isEqualTo(9_960_000L);
        assertThat(res.getFinalPriceVnd()).isEqualTo(89_640_000L);
    }

    @Test
    @DisplayName("UT-B2B-07: Custom plan with 1,500 students and 40 teachers applies 20% discount (140,800,000 VND)")
    void calculateQuote_CustomTier20PercentDiscount_1200To1799Students() {
        CalculateB2bQuoteRequest request = CalculateB2bQuoteRequest.builder()
                .selectedPackage(EnterprisePackage.ENTERPRISE_CUSTOM)
                .numberOfStudents(1500)
                .numberOfTeachers(40)
                .build();

        B2bQuoteCalculationResponse res = b2bQuoteService.calculateQuote(request);

        assertThat(res.getTotalTokenPool()).isEqualTo(1_092_000_000L);
        assertThat(res.getSubtotalVnd()).isEqualTo(176_000_000L);
        assertThat(res.getDiscountPercent()).isEqualTo(20);
        assertThat(res.getDiscountAmountVnd()).isEqualTo(35_200_000L);
        assertThat(res.getFinalPriceVnd()).isEqualTo(140_800_000L);
    }

    @Test
    @DisplayName("UT-B2B-08: Custom plan with 2,500 students and 60 teachers applies 30% discount (204,400,000 VND)")
    void calculateQuote_CustomTier30PercentDiscount_1800PlusStudents() {
        CalculateB2bQuoteRequest request = CalculateB2bQuoteRequest.builder()
                .selectedPackage(EnterprisePackage.ENTERPRISE_CUSTOM)
                .numberOfStudents(2500)
                .numberOfTeachers(60)
                .build();

        B2bQuoteCalculationResponse res = b2bQuoteService.calculateQuote(request);

        assertThat(res.getTotalTokenPool()).isEqualTo(1_813_000_000L);
        assertThat(res.getSubtotalVnd()).isEqualTo(292_000_000L);
        assertThat(res.getDiscountPercent()).isEqualTo(30);
        assertThat(res.getDiscountAmountVnd()).isEqualTo(87_600_000L);
        assertThat(res.getFinalPriceVnd()).isEqualTo(204_400_000L);
    }

    @Test
    @DisplayName("UT-B2B-09: Promo code EDU2026 adds +5% discount (total 15% for Small package)")
    void calculateQuote_WithPromoCodeEDU2026_ShouldAddExtra5PercentDiscount() {
        CalculateB2bQuoteRequest request = CalculateB2bQuoteRequest.builder()
                .selectedPackage(EnterprisePackage.ENTERPRISE_SMALL)
                .numberOfStudents(600)
                .numberOfTeachers(15)
                .promoCode("EDU2026")
                .build();

        B2bQuoteCalculationResponse res = b2bQuoteService.calculateQuote(request);

        assertThat(res.getDiscountPercent()).isEqualTo(15);
        assertThat(res.getDiscountAmountVnd()).isEqualTo(10_530_000L);
        assertThat(res.getFinalPriceVnd()).isEqualTo(59_670_000L);
        assertThat(res.getPromoCodeApplied()).isEqualTo("EDU2026");
    }

    @Test
    @DisplayName("UT-B2B-10: Submit quote request generates unique quote code and saves PENDING quote")
    void submitQuoteRequest_ShouldGenerateQuoteCodeAndSavePendingQuote() {
        SubmitB2bQuoteRequest request = SubmitB2bQuoteRequest.builder()
                .selectedPackage(EnterprisePackage.ENTERPRISE_CUSTOM)
                .numberOfStudents(850)
                .numberOfTeachers(22)
                .firstName("Văn Hiệu Trưởng")
                .lastName("Nguyễn")
                .contactEmail("hieutruong@thpt-lequydon.edu.vn")
                .contactPhone("0901234567")
                .jobTitle("Hiệu trưởng")
                .schoolName("Trường THPT Lê Quý Đôn")
                .schoolAddress("Quận 3, TP. Hồ Chí Minh")
                .message("Cần xuất hóa đơn VAT cho trường công lập")
                .build();

        when(quoteRequestRepository.existsByQuoteCodeIgnoreCase(anyString())).thenReturn(false);
        when(quoteRequestRepository.save(any(B2bQuoteRequest.class))).thenAnswer(inv -> {
            B2bQuoteRequest saved = inv.getArgument(0);
            saved.setId(UUID.randomUUID());
            saved.setCreatedAt(LocalDateTime.now());
            return saved;
        });

        B2bQuoteResponse response = b2bQuoteService.submitQuoteRequest(request);

        assertThat(response.getQuoteCode()).startsWith("QT-");
        assertThat(response.getStatus()).isEqualTo(B2bQuoteStatus.PENDING);
        assertThat(response.getSchoolName()).isEqualTo("Trường THPT Lê Quý Đôn");
        assertThat(response.getNumberOfStudents()).isEqualTo(850);
        assertThat(response.getNumberOfTeachers()).isEqualTo(22);
        assertThat(response.getTotalTokenPool()).isEqualTo(618_100_000L);
        assertThat(response.getFinalPriceVnd()).isEqualTo(89_640_000L);
    }

    @Test
    @DisplayName("UT-B2B-11: Update quote status updates status to CONTACTED and records reviewer")
    void updateQuoteStatus_ShouldUpdateStatusToContactedOrRejected() {
        UUID quoteId = UUID.randomUUID();
        UUID reviewerUid = UUID.randomUUID();
        B2bQuoteRequest existing = B2bQuoteRequest.builder()
                .id(quoteId)
                .quoteCode("QT-2026-1001")
                .selectedPackage(EnterprisePackage.ENTERPRISE_SMALL)
                .numberOfStudents(600)
                .numberOfTeachers(15)
                .totalTokenPool(435_750_000L)
                .subtotalVnd(70_200_000L)
                .finalPriceVnd(63_180_000L)
                .status(B2bQuoteStatus.PENDING)
                .build();

        when(quoteRequestRepository.findById(quoteId)).thenReturn(Optional.of(existing));
        when(quoteRequestRepository.save(any(B2bQuoteRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateB2bQuoteStatusRequest statusReq = UpdateB2bQuoteStatusRequest.builder()
                .status(B2bQuoteStatus.CONTACTED)
                .adminNote("Đã gửi hợp đồng qua email trường")
                .build();

        B2bQuoteResponse res = b2bQuoteService.updateQuoteStatus(quoteId, statusReq, reviewerUid);

        assertThat(res.getStatus()).isEqualTo(B2bQuoteStatus.CONTACTED);
        assertThat(res.getAdminNote()).isEqualTo("Đã gửi hợp đồng qua email trường");
        assertThat(existing.getReviewedByUid()).isEqualTo(reviewerUid);
    }

    @Test
    @DisplayName("UT-B2B-12: Approve quote provisions School with exact token pool, creates School Admin, and marks quote APPROVED")
    void approveQuoteAndProvisionSchool_ShouldCreateActiveSchoolAndSchoolAdminAndMarkApproved() {
        UUID quoteId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();
        UUID reviewerUid = UUID.randomUUID();
        B2bQuoteRequest quote = B2bQuoteRequest.builder()
                .id(quoteId)
                .quoteCode("QT-2026-8888")
                .selectedPackage(EnterprisePackage.ENTERPRISE_CUSTOM)
                .numberOfStudents(850)
                .numberOfTeachers(22)
                .studentDailyTokenLimit(5000)
                .teacherDailyTokenLimit(5000)
                .totalTokenPool(618_100_000L)
                .aiTokenCostVnd(92_715_000L)
                .dbAndLmsCostVnd(6_885_000L)
                .subtotalVnd(99_600_000L)
                .discountPercent(10)
                .discountAmountVnd(9_960_000L)
                .finalPriceVnd(89_640_000L)
                .firstName("Quốc Dinh")
                .lastName("Trần")
                .contactEmail("dinh@thpt-chuyen.edu.vn")
                .contactPhone("0966622100")
                .jobTitle("Hiệu trưởng")
                .schoolName("Trường THPT Chuyên Lê Quý Đôn")
                .schoolAddress("Thành phố Đà Nẵng")
                .status(B2bQuoteStatus.CONTACTED)
                .build();

        School provisionedEntity = School.builder()
                .id(schoolId)
                .name("Trường THPT Chuyên Lê Quý Đôn")
                .schoolCode("LQD-DN")
                .status(SchoolStatus.ACTIVE)
                .build();

        when(quoteRequestRepository.findById(quoteId)).thenReturn(Optional.of(quote));
        when(schoolManagementService.createSchool(any(CreateSchoolRequest.class))).thenReturn(
                SchoolResponse.builder()
                        .id(schoolId)
                        .name("Trường THPT Chuyên Lê Quý Đôn")
                        .schoolCode("LQD-DN")
                        .status(SchoolStatus.ACTIVE)
                        .build());
        when(schoolRepository.findById(schoolId)).thenReturn(Optional.of(provisionedEntity));
        when(schoolRepository.save(any(School.class))).thenAnswer(inv -> inv.getArgument(0));
        when(schoolManagementService.createSchoolAdmin(any(CreateSchoolAdminRequest.class))).thenAnswer(inv -> {
            CreateSchoolAdminRequest req = inv.getArgument(0);
            return SchoolAdminResponse.builder()
                    .uid(UUID.randomUUID())
                    .userName("lqd_dn_admin")
                    .email(req.getEmail())
                    .fullName(req.getFullName())
                    .role(UserRole.SCHOOL_ADMIN)
                    .schoolId(req.getSchoolId())
                    .initialPassword("Ht@1234567")
                    .active(true)
                    .build();
        });
        when(quoteRequestRepository.save(any(B2bQuoteRequest.class))).thenAnswer(inv -> inv.getArgument(0));

        ApproveB2bQuoteResponse result = b2bQuoteService.approveQuoteAndProvisionSchool(quoteId, reviewerUid);

        assertThat(result.getQuote().getStatus()).isEqualTo(B2bQuoteStatus.APPROVED);
        assertThat(result.getSchool().getTotalSchoolTokenQuota()).isEqualTo(618_100_000);
        assertThat(result.getSchool().getUnallocatedTokenQuota()).isEqualTo(618_100_000);
        assertThat(result.getSchool().getDefaultDailyStudentQuota()).isEqualTo(5000);
        assertThat(result.getSchool().getStatus()).isEqualTo(SchoolStatus.ACTIVE);
        assertThat(result.getSchoolAdmin().getEmail()).isEqualTo("dinh@thpt-chuyen.edu.vn");
        assertThat(result.getSchoolAdmin().getInitialPassword()).isEqualTo("Ht@1234567");

        ArgumentCaptor<School> schoolCaptor = ArgumentCaptor.forClass(School.class);
        verify(schoolRepository).save(schoolCaptor.capture());
        assertThat(schoolCaptor.getValue().getMaxStudents()).isEqualTo(850);
        assertThat(schoolCaptor.getValue().getMaxTeachers()).isEqualTo(22);
    }

    @Test
    @DisplayName("UT-B2B-14: Calculate quote with customized academic weeks and daily limits")
    void calculateQuote_DeepCustomization_Success() {
        CalculateB2bQuoteRequest request = CalculateB2bQuoteRequest.builder()
                .selectedPackage(EnterprisePackage.ENTERPRISE_CUSTOM)
                .numberOfStudents(500)
                .numberOfTeachers(20)
                .academicWeeks(40)
                .studentDailyTokenLimit(3000)
                .teacherDailyTokenLimit(10000)
                .studentDaysPerWeek(5)
                .teacherDaysPerWeek(5)
                .build();

        B2bQuoteCalculationResponse res = b2bQuoteService.calculateQuote(request);

        assertThat(res.getAcademicWeeks()).isEqualTo(40);
        assertThat(res.getStudentDailyTokenLimit()).isEqualTo(3000);
        assertThat(res.getTeacherDailyTokenLimit()).isEqualTo(10000);
        assertThat(res.getStudentDaysPerWeek()).isEqualTo(5);
        assertThat(res.getTeacherDaysPerWeek()).isEqualTo(5);

        // HS: 3000 * 5 * 40 = 600,000 / HS -> 500 HS = 300,000,000 tokens
        // GV: 10000 * 5 * 40 = 2,000,000 / GV -> 20 GV = 40,000,000 tokens
        // Total Pool = 340,000,000 tokens
        assertThat(res.getTotalTokenPool()).isEqualTo(340_000_000L);
        assertThat(res.getAiTokenCostVnd()).isEqualTo(51_000_000L); // 340M * 0.15

        // Prorated LMS & DB: 7000 * 40 / 35 = 8000; 42500 * 40 / 35 = 48571
        // DB cost: 500 * 8000 + 20 * 48571 = 4,000,000 + 971,420 = 4,971,420
        assertThat(res.getStudentDbAndQuizFeeVnd()).isEqualTo(8000L);
        assertThat(res.getTeacherLmsAndStorageFeeVnd()).isEqualTo(48571L);
        assertThat(res.getDbAndLmsCostVnd()).isEqualTo(4_971_420L);
        assertThat(res.getSubtotalVnd()).isEqualTo(55_971_420L);
        assertThat(res.getDiscountPercent()).isEqualTo(0);
        assertThat(res.getFinalPriceVnd()).isEqualTo(55_971_420L);
    }

    @Test
    @DisplayName("UT-B2B-13: Approving an already approved quote throws DataConflictException")
    void approveQuoteAndProvisionSchool_WhenAlreadyApproved_ShouldThrowDataConflictException() {
        UUID quoteId = UUID.randomUUID();
        B2bQuoteRequest approvedQuote = B2bQuoteRequest.builder()
                .id(quoteId)
                .quoteCode("QT-2026-9999")
                .status(B2bQuoteStatus.APPROVED)
                .build();

        when(quoteRequestRepository.findById(quoteId)).thenReturn(Optional.of(approvedQuote));

        assertThatThrownBy(() -> b2bQuoteService.approveQuoteAndProvisionSchool(quoteId, UUID.randomUUID()))
                .isInstanceOf(DataConflictException.class)
                .hasMessageContaining("already been approved");
    }
}
