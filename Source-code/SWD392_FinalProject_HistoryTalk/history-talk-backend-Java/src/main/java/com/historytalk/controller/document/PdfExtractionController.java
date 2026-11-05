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
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN', 'TEACHER', 'SCHOOL_ADMIN')")
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

    @PostMapping(value = {"/upload-and-extract", "/upload-and-extract/stream"}, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN', 'TEACHER', 'SCHOOL_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Upload PDF and extract text (supports SSE stream or chunked JSON keep-alive)")
    public Object uploadAndExtract(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) Integer startPage,
            @RequestParam(required = false) Integer endPage,
            HttpServletRequest request,
            HttpServletResponse response) throws IOException {
        String staffId = SecurityUtils.getUserId();
        String acceptHeader = request.getHeader("Accept");
        String uri = request.getRequestURI();
        boolean wantsSse = (acceptHeader != null && acceptHeader.contains(MediaType.TEXT_EVENT_STREAM_VALUE))
                || (uri != null && uri.endsWith("/stream"))
                || "true".equalsIgnoreCase(request.getParameter("stream"));

        if (wantsSse) {
            log.info("POST {} (SSE stream mode) by user {}, startPage={}, endPage={}", uri, staffId, startPage, endPage);
            return handleStreamExtraction(file, startPage, endPage);
        }

        log.info("POST /api/v1/documents/pdf/upload-and-extract (Chunked JSON keep-alive mode) by user {}, startPage={}, endPage={}", staffId, startPage, endPage);

        // Đọc bytes trước khi trả về streaming để giải phóng MultipartFile
        byte[] pdfBytes = file.getBytes();
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded_document.pdf";

        response.setContentType("application/json;charset=UTF-8");
        response.setHeader("X-Accel-Buffering", "no");

        return (StreamingResponseBody) outputStream -> {
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            // Bắn khoảng trắng (space ' ') mỗi 3 giây
            // Giúp Cloudflare và Reverse Proxy (Caddy/Nginx) không bao giờ bị timeout 100s,
            // đồng thời JSON.parse phía Frontend sẽ tự động bỏ qua toàn bộ leading whitespace một cách hợp lệ 100%.
            ScheduledFuture<?> heartbeat = scheduler.scheduleAtFixedRate(() -> {
                try {
                    synchronized (outputStream) {
                        outputStream.write(' ');
                        outputStream.flush();
                    }
                } catch (Exception ignored) {
                }
            }, 0, 3, TimeUnit.SECONDS);

            try {
                PdfExtractionResponse result = pdfExtractionService.extractTextFromBytes(pdfBytes, startPage, endPage);
                if (result.getFileUrl() == null || result.getFileUrl().isBlank()) {
                    result.setFileUrl(originalFilename);
                }

                ApiResponse<PdfExtractionResponse> apiResponse = ApiResponse.success(result, "PDF text extracted successfully");
                byte[] jsonBytes = objectMapper.writeValueAsBytes(apiResponse);

                synchronized (outputStream) {
                    outputStream.write(jsonBytes);
                    outputStream.flush();
                }
            } catch (Exception e) {
                log.error("Synchronous PDF extraction failed: {}", e.getMessage(), e);
                try {
                    ApiResponse<Void> errorResponse = ApiResponse.error("Trích xuất PDF thất bại: " + e.getMessage(), "PDF_EXTRACTION_ERROR");
                    byte[] errorBytes = objectMapper.writeValueAsBytes(errorResponse);
                    synchronized (outputStream) {
                        outputStream.write(errorBytes);
                        outputStream.flush();
                    }
                } catch (Exception ignored) {
                }
            } finally {
                heartbeat.cancel(true);
                scheduler.shutdown();
            }
        };
    }

    private SseEmitter handleStreamExtraction(
            MultipartFile file,
            Integer startPage,
            Integer endPage) {

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
                PdfExtractionResponse result = pdfExtractionService.extractText(file, startPage, endPage);
                if (result.getFileUrl() == null || result.getFileUrl().isBlank()) {
                    result.setFileUrl(file.getOriginalFilename() != null ? file.getOriginalFilename() : "uploaded_document.pdf");
                }

                int totalPages = result.getPageCount();
                for (int i = 1; i <= totalPages; i++) {
                    String progressJson = String.format("{\"type\":\"progress\",\"page\":%d,\"total\":%d}", i, totalPages);
                    emitter.send(SseEmitter.event().data(progressJson));
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
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN', 'TEACHER', 'SCHOOL_ADMIN')")
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
