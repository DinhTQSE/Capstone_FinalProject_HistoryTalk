package com.historytalk.controller.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.document.DocumentFileResponse;
import com.historytalk.dto.document.PdfExtractionResponse;
import com.historytalk.dto.document.SaveDocumentRequest;
import com.historytalk.entity.enums.ContentStatus;
import com.historytalk.exception.GlobalExceptionHandler;
import com.historytalk.security.AuthenticatedPrincipal;
import com.historytalk.service.document.DocumentContentService;
import com.historytalk.service.document.PdfExtractionRateLimiter;
import com.historytalk.service.document.PdfExtractionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PdfExtractionControllerTest {

    private MockMvc mockMvc;

    @Mock
    private PdfExtractionService pdfExtractionService;

    @Mock
    private DocumentContentService documentContentService;

    @Mock
    private PdfExtractionRateLimiter pdfExtractionRateLimiter;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PdfExtractionController pdfExtractionController;
    private String staffId;

    @BeforeEach
    void setUp() {
        staffId = UUID.randomUUID().toString();
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                "content_admin@historytalk.vn", staffId, "CONTENT_ADMIN");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_CONTENT_ADMIN"))
                )
        );

        pdfExtractionController = new PdfExtractionController(
                pdfExtractionService,
                documentContentService,
                pdfExtractionRateLimiter,
                objectMapper
        );

        mockMvc = MockMvcBuilders.standaloneSetup(pdfExtractionController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("IT-PDF-01 (WF-02 / F-06): POST /api/v1/documents/pdf/extract acquires rate lock, extracts PDF text, and releases lock")
    void extractText_ValidPdf_Returns200AndReleasesRateLimitLock() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "bach_dang_1288.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.4 sample content".getBytes(StandardCharsets.UTF_8)
        );

        PdfExtractionResponse extracted = PdfExtractionResponse.builder()
                .fileUrl("bach_dang_1288.pdf")
                .rawText("Chiến thắng Bạch Đằng năm 1288 do Hưng Đạo Đại Vương chỉ huy.")
                .pageCount(3)
                .build();

        when(pdfExtractionRateLimiter.acquire(eq(staffId), eq(true))).thenReturn("lock-token-01");
        when(pdfExtractionService.extractText(any(), eq(1), eq(3), eq(true), eq(false), isNull(), isNull())).thenReturn(extracted);

        mockMvc.perform(multipart("/api/v1/documents/pdf/extract")
                        .file(file)
                        .param("startPage", "1")
                        .param("endPage", "3")
                        .param("enableAiVision", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pageCount").value(3))
                .andExpect(jsonPath("$.data.rawText").value("Chiến thắng Bạch Đằng năm 1288 do Hưng Đạo Đại Vương chỉ huy."));

        verify(pdfExtractionRateLimiter).acquire(eq(staffId), eq(true));
        verify(pdfExtractionRateLimiter).release(eq(staffId), eq("lock-token-01"));
    }

    @Test
    @DisplayName("IT-PDF-02 (WF-02 / F-06): POST /api/v1/documents/pdf/save-content saves reviewed markdown content")
    void saveDocumentContent_ValidRequest_Returns200() throws Exception {
        String docId = UUID.randomUUID().toString();
        SaveDocumentRequest req = SaveDocumentRequest.builder()
                .docId(docId)
                .content("# Chiến thắng Bạch Đằng 1288\nNội dung đã biên tập")
                .status(ContentStatus.ACTIVE)
                .build();

        DocumentFileResponse saved = DocumentFileResponse.builder()
                .docId(docId)
                .title("Chiến thắng Bạch Đằng 1288")
                .build();

        when(documentContentService.saveDocumentContent(any(SaveDocumentRequest.class), eq(staffId), eq("CONTENT_ADMIN")))
                .thenReturn(saved);

        mockMvc.perform(post("/api/v1/documents/pdf/save-content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.docId").value(docId))
                .andExpect(jsonPath("$.data.title").value("Chiến thắng Bạch Đằng 1288"));
    }
}
