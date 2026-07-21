package com.historytalk.controller.document;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.document.DocumentFileResponse;
import com.historytalk.dto.document.PdfExtractionResponse;
import com.historytalk.dto.document.SaveDocumentRequest;
import com.historytalk.service.document.DocumentContentService;
import com.historytalk.service.document.PdfExtractionService;
import com.historytalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/documents/pdf")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "PDF Extraction", description = "PDF text extraction and document content management APIs")
public class PdfExtractionController {

    private final PdfExtractionService pdfExtractionService;
    private final DocumentContentService documentContentService;

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Extract raw text from PDF file for frontend drafting (STATELESS - no DB persistence)")
    public ResponseEntity<ApiResponse<PdfExtractionResponse>> extractText(
            @RequestPart("file") MultipartFile file) throws IOException {
        String staffId = SecurityUtils.getUserId();
        log.info("POST /api/v1/documents/pdf/extract by user {}", staffId);
        PdfExtractionResponse response = pdfExtractionService.extractText(file);
        return ResponseEntity.ok(ApiResponse.success(response, "PDF text extracted successfully"));
    }

    @PostMapping("/{docId}/save-content")
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Save user-edited content to document")
    public ResponseEntity<ApiResponse<DocumentFileResponse>> saveContent(
            @RequestBody @Valid SaveDocumentRequest request) {
        String staffId = SecurityUtils.getUserId();
        String staffRole = SecurityUtils.getRoleName();
        log.info("POST /api/v1/documents/{}/save-content by user {}", request.getDocId(), staffId);
        DocumentFileResponse response = documentContentService.saveDocumentContent(request, staffId, staffRole);
        return ResponseEntity.ok(ApiResponse.success(response, "Document content saved successfully"));
    }
}
