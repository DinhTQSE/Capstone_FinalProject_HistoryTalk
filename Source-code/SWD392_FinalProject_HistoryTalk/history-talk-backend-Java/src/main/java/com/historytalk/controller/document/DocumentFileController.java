package com.historytalk.controller.document;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.document.DocumentFileResponse;
import com.historytalk.dto.document.DocumentPdfUrlResponse;
import com.historytalk.service.document.DownloadedDocumentFile;
import com.historytalk.service.document.DocumentFileService;
import com.historytalk.service.document.DocumentPdfUrl;
import com.historytalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Documents", description = "Generic document file APIs")
public class DocumentFileController {

    private final DocumentFileService documentFileService;

    @PostMapping(value = "/{docId}/upload-pdf", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Upload PDF file for an existing document")
    public ResponseEntity<ApiResponse<DocumentFileResponse>> uploadPdfFile(
            @PathVariable String docId,
            @RequestPart("file") MultipartFile file) {
        String staffId = SecurityUtils.getUserId();
        String staffRole = SecurityUtils.getRoleName();
        log.info("POST /api/v1/documents/{}/upload-pdf by user {}", docId, staffId);
        DocumentFileResponse document = documentFileService.uploadPdfFile(docId, file, staffId, staffRole);
        return ResponseEntity.ok(ApiResponse.success(document, "PDF file uploaded successfully"));
    }

    // @GetMapping("/{docId}/download-pdf")
    // @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    // @SecurityRequirement(name = "bearerAuth")
    // @Operation(summary = "Download PDF file for an existing document")
    // public ResponseEntity<byte[]> downloadPdfFile(@PathVariable String docId) {
    //     String staffId = SecurityUtils.getUserId();
    //     String staffRole = SecurityUtils.getRoleName();
    //     log.info("GET /api/v1/documents/{}/download-pdf by user {}", docId, staffId);
    //     DownloadedDocumentFile file = documentFileService.downloadPdfFile(docId, staffId, staffRole);
    //     return ResponseEntity.ok()
    //             .contentType(MediaType.APPLICATION_PDF)
    //             .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + docId + ".pdf\"")
    //             .body(file.bytes());
    // }

    @GetMapping("/{docId}/pdf-url")
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a signed Supabase URL for an existing PDF document")
    public ResponseEntity<ApiResponse<DocumentPdfUrlResponse>> createPdfUrl(@PathVariable String docId) {
        String staffId = SecurityUtils.getUserId();
        String staffRole = SecurityUtils.getRoleName();
        log.info("GET /api/v1/documents/{}/pdf-url by user {}", docId, staffId);
        DocumentPdfUrl signedUrl = documentFileService.createPdfUrl(docId, staffId, staffRole);
        DocumentPdfUrlResponse response = DocumentPdfUrlResponse.builder()
                .url(signedUrl.url())
                .expiresIn(signedUrl.expiresIn())
                .build();
        return ResponseEntity.ok(ApiResponse.success(response, "PDF URL generated successfully"));
    }
}
