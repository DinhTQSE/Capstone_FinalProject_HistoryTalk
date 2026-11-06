package com.historytalk.controller.document;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/documents/pdf")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "PDF Extraction", description = "PDF text extraction and document content management APIs")
public class PdfExtractionController {

    private final PdfExtractionService pdfExtractionService;
    private final DocumentContentService documentContentService;
    private final ObjectMapper objectMapper;

    @PostMapping(value = "/extract", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'TEACHER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Extract raw text from PDF file for frontend drafting (STATELESS - no DB persistence)")
    public ResponseEntity<ApiResponse<PdfExtractionResponse>> extractText(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Integer startPage,
            @RequestParam(required = false) Integer endPage) throws IOException {
        String staffId = SecurityUtils.getUserId();
        log.info("POST /api/v1/documents/pdf/extract by user {}, startPage={}, endPage={}", staffId, startPage, endPage);
        PdfExtractionResponse response = pdfExtractionService.extractText(file, startPage, endPage);
        return ResponseEntity.ok(ApiResponse.success(response, "PDF text extracted successfully"));
    }

    @PostMapping(
            value = {"/upload-and-extract", "/upload-and-extract/stream"},
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE
    )
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'TEACHER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Upload PDF and stream extraction progress via SSE directly on /upload-and-extract")
    public SseEmitter uploadAndExtract(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Integer startPage,
            @RequestParam(required = false) Integer endPage) {
        String staffId = SecurityUtils.getUserId();
        log.info("POST /api/v1/documents/pdf/upload-and-extract (SSE stream) by user {}, startPage={}, endPage={}", staffId, startPage, endPage);

        SseEmitter emitter = new SseEmitter(15 * 60 * 1000L);

        // Heartbeat executor: Bắn SSE comment ": ping\n\n" mỗi 3 giây
        // Giúp Cloudflare, Caddy và Trình duyệt duy trì kết nối liên tục, chống rớt mạng / timeout 524
        ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
        ScheduledFuture<?> heartbeatFuture = heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (Exception ignored) {
            }
        }, 1, 3, TimeUnit.SECONDS);

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                PdfExtractionResponse result = pdfExtractionService.extractText(
                        file,
                        startPage,
                        endPage,
                        (page, total) -> {
                            try {
                                String progressJson = String.format("{\"type\":\"progress\",\"page\":%d,\"total\":%d}", page, total);
                                emitter.send(SseEmitter.event().data(progressJson));
                            } catch (Exception ignored) {
                            }
                        }
                );
                if (result.getFileUrl() == null || result.getFileUrl().isBlank()) {
                    result.setFileUrl(file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded_document.pdf");
                }

                String dataJson = objectMapper.writeValueAsString(result);
                String doneJson = String.format("{\"type\":\"done\",\"data\":%s}", dataJson);
                emitter.send(SseEmitter.event().data(doneJson));
                emitter.complete();
            } catch (Exception e) {
                log.error("PDF stream extraction failed: {}", e.getMessage(), e);
                try {
                    String errorJson = String.format("{\"type\":\"error\",\"message\":\"%s\"}", e.getMessage());
                    emitter.send(SseEmitter.event().data(errorJson));
                } catch (Exception ex) {
                    // Ignore SSE send error
                }
                emitter.completeWithError(e);
            } finally {
                heartbeatFuture.cancel(true);
                heartbeatScheduler.shutdown();
            }
        });

        return emitter;
    }

    @PostMapping(value = "/save-content")
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'TEACHER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Save or update document content and status (Phase 1 stateless PDF upload -> edit -> save)")
    public ResponseEntity<ApiResponse<DocumentFileResponse>> saveDocumentContent(
            @Valid @RequestBody SaveDocumentRequest request) {
        String staffId = SecurityUtils.getUserId();
        String staffRole = SecurityUtils.getRoleName();
        log.info("POST /api/v1/documents/pdf/save-content for docId {} by user {}", request.getDocId(), staffId);
        DocumentFileResponse response = documentContentService.saveDocumentContent(request, staffId, staffRole);
        return ResponseEntity.ok(ApiResponse.success(response, "Nội dung tài liệu đã được lưu thành công"));
    }
}
