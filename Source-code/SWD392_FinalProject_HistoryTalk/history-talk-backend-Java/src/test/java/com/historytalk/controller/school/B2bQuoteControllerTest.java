package com.historytalk.controller.school;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.historytalk.dto.PaginatedResponse;
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
import com.historytalk.exception.GlobalExceptionHandler;
import com.historytalk.service.school.B2bQuoteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class B2bQuoteControllerTest {

    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Mock
    private B2bQuoteService b2bQuoteService;

    @InjectMocks
    private B2bQuoteController b2bQuoteController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(b2bQuoteController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("IT-B2B-01: GET /api/v1/b2b/plans returns 200 OK with B2B plan catalog")
    void getPlanCatalog_Public_Returns200() throws Exception {
        B2bPlanCatalogResponse catalog = B2bPlanCatalogResponse.builder()
                .academicWeeksPerYear(35)
                .tokenUnitPriceVnd(0.15)
                .studentTotalAnnualCostVnd(112_000L)
                .teacherTotalAnnualCostVnd(200_000L)
                .plans(List.of(
                        B2bPlanCatalogResponse.FixedPlanItem.builder()
                                .packageType(EnterprisePackage.ENTERPRISE_SMALL)
                                .displayName("Gói Trường Nhỏ / THCS (Small)")
                                .maxStudents(600)
                                .maxTeachers(15)
                                .totalTokenPool(435_750_000L)
                                .finalPriceVnd(63_180_000L)
                                .build()
                ))
                .build();

        when(b2bQuoteService.getB2bPlanCatalog()).thenReturn(catalog);

        mockMvc.perform(get("/api/v1/b2b/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.academicWeeksPerYear").value(35))
                .andExpect(jsonPath("$.data.tokenUnitPriceVnd").value(0.15))
                .andExpect(jsonPath("$.data.plans[0].packageType").value("ENTERPRISE_SMALL"))
                .andExpect(jsonPath("$.data.plans[0].finalPriceVnd").value(63180000));
    }

    @Test
    @DisplayName("IT-B2B-02: POST /api/v1/b2b/quotes/calculate returns 200 OK with live quote breakdown")
    void calculateQuote_PublicFixedAndCustom_Returns200() throws Exception {
        CalculateB2bQuoteRequest request = CalculateB2bQuoteRequest.builder()
                .selectedPackage(EnterprisePackage.ENTERPRISE_CUSTOM)
                .numberOfStudents(850)
                .numberOfTeachers(22)
                .build();

        B2bQuoteCalculationResponse calcResponse = B2bQuoteCalculationResponse.builder()
                .selectedPackage(EnterprisePackage.ENTERPRISE_CUSTOM)
                .numberOfStudents(850)
                .numberOfTeachers(22)
                .totalTokenPool(618_100_000L)
                .aiTokenCostVnd(92_715_000L)
                .dbAndLmsCostVnd(6_885_000L)
                .subtotalVnd(99_600_000L)
                .discountPercent(10)
                .discountAmountVnd(9_960_000L)
                .finalPriceVnd(89_640_000L)
                .build();

        when(b2bQuoteService.calculateQuote(any(CalculateB2bQuoteRequest.class))).thenReturn(calcResponse);

        mockMvc.perform(post("/api/v1/b2b/quotes/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.numberOfStudents").value(850))
                .andExpect(jsonPath("$.data.numberOfTeachers").value(22))
                .andExpect(jsonPath("$.data.totalTokenPool").value(618100000))
                .andExpect(jsonPath("$.data.discountPercent").value(10))
                .andExpect(jsonPath("$.data.finalPriceVnd").value(89640000));
    }

    @Test
    @DisplayName("IT-B2B-03: POST /api/v1/b2b/quotes/calculate with invalid student count returns 400 Bad Request")
    void calculateQuote_InvalidStudentCount_Returns400() throws Exception {
        String invalidJson = "{\"numberOfStudents\": 0, \"numberOfTeachers\": 15}";

        mockMvc.perform(post("/api/v1/b2b/quotes/calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("IT-B2B-04: POST /api/v1/b2b/quotes/request returns 201 Created with quoteCode")
    void submitQuoteRequest_PublicValid_Returns201() throws Exception {
        SubmitB2bQuoteRequest request = SubmitB2bQuoteRequest.builder()
                .selectedPackage(EnterprisePackage.ENTERPRISE_SMALL)
                .numberOfStudents(600)
                .numberOfTeachers(15)
                .firstName("Văn Hiệu Trưởng")
                .lastName("Nguyễn")
                .contactEmail("hieutruong@thpt-lequydon.edu.vn")
                .contactPhone("0901234567")
                .jobTitle("Hiệu trưởng")
                .schoolName("Trường THPT Lê Quý Đôn")
                .schoolAddress("Quận 3, TP. Hồ Chí Minh")
                .build();

        B2bQuoteResponse savedResponse = B2bQuoteResponse.builder()
                .id(UUID.randomUUID())
                .quoteCode("QT-2026-1234")
                .selectedPackage(EnterprisePackage.ENTERPRISE_SMALL)
                .numberOfStudents(600)
                .numberOfTeachers(15)
                .totalTokenPool(435_750_000L)
                .finalPriceVnd(63_180_000L)
                .schoolName("Trường THPT Lê Quý Đôn")
                .status(B2bQuoteStatus.PENDING)
                .build();

        when(b2bQuoteService.submitQuoteRequest(any(SubmitB2bQuoteRequest.class))).thenReturn(savedResponse);

        mockMvc.perform(post("/api/v1/b2b/quotes/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.quoteCode").value("QT-2026-1234"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.finalPriceVnd").value(63180000));
    }

    @Test
    @DisplayName("IT-B2B-05: POST /api/v1/b2b/quotes/request with missing contactEmail returns 400 Bad Request")
    void submitQuoteRequest_MissingRequiredFields_Returns400() throws Exception {
        String invalidJson = """
                {
                  "selectedPackage": "ENTERPRISE_SMALL",
                  "firstName": "Văn A",
                  "lastName": "Nguyễn",
                  "schoolName": "THPT A"
                }
                """;

        mockMvc.perform(post("/api/v1/b2b/quotes/request")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("IT-B2B-06: GET /api/v1/admin/b2b-quotes returns paginated quote requests")
    void getQuotes_SystemAdmin_Returns200() throws Exception {
        B2bQuoteResponse item = B2bQuoteResponse.builder()
                .id(UUID.randomUUID())
                .quoteCode("QT-2026-1234")
                .schoolName("Trường THPT Lê Quý Đôn")
                .status(B2bQuoteStatus.PENDING)
                .finalPriceVnd(63_180_000L)
                .build();

        PaginatedResponse<B2bQuoteResponse> page = PaginatedResponse.<B2bQuoteResponse>builder()
                .content(List.of(item))
                .totalElements(1L)
                .totalPages(1)
                .currentPage(1)
                .pageSize(10)
                .hasNext(false)
                .hasPrevious(false)
                .build();

        when(b2bQuoteService.getQuoteRequests(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/v1/admin/b2b-quotes")
                        .param("status", "PENDING")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].quoteCode").value("QT-2026-1234"));
    }

    @Test
    @DisplayName("IT-B2B-07: PATCH /api/v1/admin/b2b-quotes/{id}/status updates status to CONTACTED")
    void updateQuoteStatus_SystemAdmin_Returns200() throws Exception {
        UUID quoteId = UUID.randomUUID();
        UpdateB2bQuoteStatusRequest req = UpdateB2bQuoteStatusRequest.builder()
                .status(B2bQuoteStatus.CONTACTED)
                .adminNote("Đã liên hệ qua số điện thoại")
                .build();

        B2bQuoteResponse updated = B2bQuoteResponse.builder()
                .id(quoteId)
                .quoteCode("QT-2026-1234")
                .status(B2bQuoteStatus.CONTACTED)
                .adminNote("Đã liên hệ qua số điện thoại")
                .build();

        when(b2bQuoteService.updateQuoteStatus(eq(quoteId), any(UpdateB2bQuoteStatusRequest.class), any()))
                .thenReturn(updated);

        mockMvc.perform(patch("/api/v1/admin/b2b-quotes/{id}/status", quoteId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CONTACTED"));
    }

    @Test
    @DisplayName("IT-B2B-08: POST /api/v1/admin/b2b-quotes/{id}/approve provisions School and School Admin")
    void approveQuoteAndProvisionSchool_SystemAdmin_Returns200() throws Exception {
        UUID quoteId = UUID.randomUUID();
        UUID schoolId = UUID.randomUUID();

        ApproveB2bQuoteResponse approveRes = ApproveB2bQuoteResponse.builder()
                .quote(B2bQuoteResponse.builder()
                        .id(quoteId)
                        .quoteCode("QT-2026-1234")
                        .status(B2bQuoteStatus.APPROVED)
                        .createdSchoolId(schoolId)
                        .build())
                .school(SchoolResponse.builder()
                        .id(schoolId)
                        .name("Trường THPT Lê Quý Đôn")
                        .schoolCode("LQD-HCM")
                        .totalSchoolTokenQuota(435_750_000)
                        .unallocatedTokenQuota(435_750_000)
                        .status(SchoolStatus.ACTIVE)
                        .build())
                .schoolAdmin(SchoolAdminResponse.builder()
                        .uid(UUID.randomUUID())
                        .userName("lqd_hcm_admin")
                        .email("hieutruong@thpt-lequydon.edu.vn")
                        .role(UserRole.SCHOOL_ADMIN)
                        .initialPassword("Ht@9876543")
                        .build())
                .build();

        when(b2bQuoteService.approveQuoteAndProvisionSchool(eq(quoteId), any()))
                .thenReturn(approveRes);

        mockMvc.perform(post("/api/v1/admin/b2b-quotes/{id}/approve", quoteId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.quote.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.school.schoolCode").value("LQD-HCM"))
                .andExpect(jsonPath("$.data.school.totalSchoolTokenQuota").value(435750000))
                .andExpect(jsonPath("$.data.schoolAdmin.userName").value("lqd_hcm_admin"))
                .andExpect(jsonPath("$.data.schoolAdmin.initialPassword").value("Ht@9876543"));
    }
}
