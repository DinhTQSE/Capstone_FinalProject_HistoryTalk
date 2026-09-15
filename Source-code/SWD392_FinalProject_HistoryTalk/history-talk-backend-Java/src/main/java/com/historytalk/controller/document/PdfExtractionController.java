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

    @PostMapping(value = "/upload-and-extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Upload PDF and extract raw text for document drafting")
    public ResponseEntity<ApiResponse<PdfExtractionResponse>> uploadAndExtract(
            @RequestPart("file") MultipartFile file,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String entityType,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String entityId) throws IOException {
        String staffId = SecurityUtils.getUserId();
        log.info("POST /api/v1/documents/pdf/upload-and-extract by user {} for entityType={}, entityId={}", staffId, entityType, entityId);
        PdfExtractionResponse response = pdfExtractionService.extractText(file);
        if (response.getFileUrl() == null || response.getFileUrl().isBlank()) {
            response.setFileUrl(file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded_document.pdf");
        }
        return ResponseEntity.ok(ApiResponse.success(response, "PDF text extracted successfully"));
    }

    @PostMapping(value = "/upload-and-extract/stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Upload PDF and stream extraction progress via SSE")
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter uploadAndExtractStream(
            @RequestPart("file") MultipartFile file,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String entityType,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String entityId) {
        String staffId = SecurityUtils.getUserId();
        log.info("POST /api/v1/documents/pdf/upload-and-extract/stream by user {}", staffId);
        
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(15 * 60 * 1000L);
        
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                PdfExtractionResponse result = pdfExtractionService.extractText(file);
                if (result.getFileUrl() == null || result.getFileUrl().isBlank()) {
                    result.setFileUrl(file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded_document.pdf");
                }
                
                int totalPages = result.getPageCount();
                for (int i = 1; i <= totalPages; i++) {
                    String progressJson = String.format("{\"type\":\"progress\",\"page\":%d,\"total\":%d}", i, totalPages);
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().data(progressJson));
                }
                
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                String dataJson = mapper.writeValueAsString(result);
                String doneJson = String.format("{\"type\":\"done\",\"data\":%s}", dataJson);
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().data(doneJson));
                emitter.complete();
            } catch (Exception e) {
                log.error("PDF stream extraction failed: {}", e.getMessage(), e);
                try {
                    String errorJson = String.format("{\"type\":\"error\",\"message\":\"%s\"}", e.getMessage());
                    emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event().data(errorJson));
                } catch (Exception ex) {
                    // Ignore SSE send error
                }
                emitter.completeWithError(e);
            }
        });
        
        return emitter;
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
