package com.historytalk.service.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.SystemException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Service
public class SupabaseDocumentStorageService {

    private static final long MAX_FILE_BYTES = 50 * 1024 * 1024;

    private final RestClient restClient;
    private final String supabaseUrl;
    private final String bucket;

    public SupabaseDocumentStorageService(
            @Value("${supabase.url}") String supabaseUrl,
            @Value("${supabase.service-role-key}") String serviceRoleKey,
            @Value("${supabase.storage.bucket:documents}") String bucket,
            RestClient.Builder restClientBuilder) {
        this.supabaseUrl = supabaseUrl.replaceAll("/+$", "");
        this.bucket = bucket;
        this.restClient = restClientBuilder
                .baseUrl(this.supabaseUrl)
                .defaultHeader("Authorization", "Bearer " + serviceRoleKey)
                .defaultHeader("apikey", serviceRoleKey)
                .build();
    }

    public UploadedDocumentFile uploadPdf(EntityType entityType, UUID entityId, UUID docId, MultipartFile file) {
        validatePdf(file);
        String objectPath = "documents/%s/%s/%s.pdf".formatted(
                entityType.name().toLowerCase(Locale.ROOT),
                entityId,
                docId);

        try {
            restClient.post()
                    .uri("/storage/v1/object/" + bucket + "/" + objectPath)
                    .contentType(MediaType.APPLICATION_PDF)
                    .header("x-upsert", "true")
                    .body(file.getBytes())
                    .retrieve()
                    .toBodilessEntity();
            return new UploadedDocumentFile(objectPath);
        } catch (IOException ex) {
            throw new SystemException("Không thể đọc file PDF: " + ex.getMessage());
        } catch (RestClientException ex) {
            throw new SystemException("Không thể tải file PDF lên Supabase: " + ex.getMessage());
        }
    }

    public DownloadedDocumentFile downloadPdf(String objectPath) {
        try {
            byte[] bytes = restClient.get()
                    .uri("/storage/v1/object/" + bucket + "/" + objectPath)
                    .retrieve()
                    .body(byte[].class);
            if (bytes == null || bytes.length == 0) {
                throw new SystemException("File PDF được tải xuống trống");
            }
            return new DownloadedDocumentFile(bytes, MediaType.APPLICATION_PDF_VALUE);
        } catch (RestClientException ex) {
            throw new SystemException("Không thể tải file PDF từ Supabase: " + ex.getMessage());
        }
    }

    public DocumentPdfUrl createSignedPdfUrl(String objectPath, long expiresInSeconds, String downloadFileName) {
        try {
            SignedUrlResponse response = restClient.post()
                    .uri("/storage/v1/object/sign/" + bucket + "/" + objectPath)
                    .body(new SignedUrlRequest(expiresInSeconds))
                    .retrieve()
                    .body(SignedUrlResponse.class);
            if (response == null || response.signedUrl() == null || response.signedUrl().isBlank()) {
                throw new SystemException("Supabase không trả về URL PDF có chữ ký");
            }
            String signedUrl = response.signedUrl();
            if (signedUrl.startsWith("/storage/v1/")) {
                signedUrl = supabaseUrl + signedUrl;
            } else if (signedUrl.startsWith("/object/")) {
                signedUrl = supabaseUrl + "/storage/v1" + signedUrl;
            } else if (signedUrl.startsWith("/")) {
                signedUrl = supabaseUrl + signedUrl;
            }
            return new DocumentPdfUrl(appendDownloadParam(signedUrl, downloadFileName), expiresInSeconds);
        } catch (RestClientException ex) {
            throw new SystemException("Không thể tạo URL PDF có chữ ký từ Supabase: " + ex.getMessage());
        }
    }

    private String appendDownloadParam(String signedUrl, String downloadFileName) {
        if (downloadFileName == null || downloadFileName.isBlank()) {
            return signedUrl;
        }
        String separator = signedUrl.contains("?") ? "&" : "?";
        String encodedFileName = URLEncoder.encode(downloadFileName, StandardCharsets.UTF_8);
        return signedUrl + separator + "download=" + encodedFileName;
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("File PDF không được để trống");
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new InvalidRequestException("Kích thước file PDF vượt quá giới hạn 50MB");
        }
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new InvalidRequestException("Chỉ chấp nhận file .pdf");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && !"application/pdf".equalsIgnoreCase(contentType)) {
            throw new InvalidRequestException("Chỉ chấp nhận file application/pdf");
        }
    }

    private record SignedUrlRequest(@JsonProperty("expiresIn") long expiresIn) {
    }

    private record SignedUrlResponse(@JsonProperty("signedURL") String signedUrl) {
    }
}
