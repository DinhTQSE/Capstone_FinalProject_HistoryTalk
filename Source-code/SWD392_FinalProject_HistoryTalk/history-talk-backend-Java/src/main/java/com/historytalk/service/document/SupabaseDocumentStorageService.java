package com.historytalk.service.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.SystemException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
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
        this.bucket = (bucket != null) ? bucket.trim() : "documents";
        this.restClient = restClientBuilder
                .baseUrl(this.supabaseUrl)
                .defaultHeader("Authorization", "Bearer " + serviceRoleKey)
                .defaultHeader("apikey", serviceRoleKey)
                .build();
    }

    public UploadedDocumentFile uploadPdf(EntityType entityType, UUID entityId, UUID docId, MultipartFile file) {
        validatePdf(file);
        String objectPath = "%s/%s/%s.pdf".formatted(
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
            String cleanPath = objectPath.startsWith(bucket + "/") 
                    ? objectPath.substring(bucket.length() + 1) 
                    : objectPath;
            byte[] bytes = restClient.get()
                    .uri("/storage/v1/object/" + bucket + "/" + cleanPath)
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
            String cleanPath = objectPath.startsWith(bucket + "/") 
                    ? objectPath.substring(bucket.length() + 1) 
                    : objectPath;
            SignedUrlResponse response = restClient.post()
                    .uri("/storage/v1/object/sign/" + bucket + "/" + cleanPath)
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

    public String generatePresignedUploadUrl(String storagePath, String contentType, long expiresIn) {
        try {
            // Strip bucket prefix from storagePath to avoid duplication
            String cleanPath = storagePath.startsWith(bucket + "/") 
                    ? storagePath.substring(bucket.length() + 1) 
                    : storagePath;
            
            String fullEndpoint = "/storage/v1/object/upload/sign/" + bucket + "/" + cleanPath;
            log.info("[SUPABASE DEBUG] Final Endpoint: {}", fullEndpoint);
            log.info("[SUPABASE DEBUG] Bucket: '{}', Path: '{}', CleanPath: '{}'", bucket, storagePath, cleanPath);
            
            // Log raw response before mapping
            String rawResponseBody = restClient.post()
                    .uri(fullEndpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PresignedUploadRequest(expiresIn))
                    .retrieve()
                    .body(String.class);
            
            log.info("[SUPABASE DEBUG] Raw Response Body: {}", rawResponseBody);
            
            // Map response
            PresignedUploadResponse response = restClient.post()
                    .uri(fullEndpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PresignedUploadRequest(expiresIn))
                    .retrieve()
                    .body(PresignedUploadResponse.class);
            
            String signedUrl = response.getEffectiveUrl();
            if (signedUrl == null || signedUrl.isBlank()) {
                throw new SystemException("Supabase không trả về presigned upload URL");
            }
            if (signedUrl.startsWith("/storage/v1/")) {
                signedUrl = supabaseUrl + signedUrl;
            } else if (signedUrl.startsWith("/")) {
                signedUrl = supabaseUrl + signedUrl;
            }
            return signedUrl;
        } catch (RestClientException ex) {
            throw new SystemException("Không thể tạo presigned upload URL từ Supabase: " + ex.getMessage());
        }
    }

    public String createSignedUrl(String storagePath, long expiresIn) {
        try {
            String cleanPath = storagePath.startsWith(bucket + "/") 
                    ? storagePath.substring(bucket.length() + 1) 
                    : storagePath;
            SignedUrlResponse response = restClient.post()
                    .uri("/storage/v1/object/sign/" + bucket + "/" + cleanPath)
                    .body(new SignedUrlRequest(expiresIn))
                    .retrieve()
                    .body(SignedUrlResponse.class);
            if (response == null || response.signedUrl() == null || response.signedUrl().isBlank()) {
                throw new SystemException("Supabase không trả về signed URL");
            }
            String signedUrl = response.signedUrl();
            if (signedUrl.startsWith("/storage/v1/")) {
                signedUrl = supabaseUrl + signedUrl;
            } else if (signedUrl.startsWith("/")) {
                signedUrl = supabaseUrl + signedUrl;
            }
            return signedUrl;
        } catch (RestClientException ex) {
            throw new SystemException("Không thể tạo signed URL từ Supabase: " + ex.getMessage());
        }
    }

    public void deleteFile(String storagePath) {
        try {
            // Strip bucket prefix from storagePath to avoid duplication
            String cleanPath = storagePath.startsWith(bucket + "/") 
                    ? storagePath.substring(bucket.length() + 1) 
                    : storagePath;
            
            restClient.delete()
                    .uri("/storage/v1/object/" + bucket + "/" + cleanPath)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new SystemException("Không thể xóa file từ Supabase: " + ex.getMessage());
        }
    }

    /**
     * Upload file directly to Supabase Storage using InputStream.
     * Used for direct binary upload strategy (lightweight files).
     *
     * @param storagePath Storage path for the file
     * @param inputStream InputStream of the file data
     * @param contentType Content type of the file
     * @param fileSizeBytes Size of the file in bytes
     * @throws IOException if stream reading fails
     */
    public void uploadFile(String storagePath, InputStream inputStream, String contentType, long fileSizeBytes) throws IOException {
        try {
            // Strip bucket prefix from storagePath to avoid duplication
            String cleanPath = storagePath.startsWith(bucket + "/") 
                    ? storagePath.substring(bucket.length() + 1) 
                    : storagePath;
            
            byte[] bytes = inputStream.readAllBytes();
            restClient.post()
                    .uri("/storage/v1/object/" + bucket + "/" + cleanPath)
                    .contentType(MediaType.parseMediaType(contentType))
                    .header("x-upsert", "true")
                    .body(bytes)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new SystemException("Không thể tải file lên Supabase: " + ex.getMessage());
        }
    }

    /**
     * Build a permanent public URL for an object in the documents bucket.
     */
    public String buildPublicUrl(String storagePath) {
        String cleanPath = storagePath.startsWith(bucket + "/") 
                ? storagePath.substring(bucket.length() + 1) 
                : storagePath;
        String cleanBase = supabaseUrl.replaceAll("/+$", "");
        return cleanBase + "/storage/v1/object/public/" + bucket + "/" + cleanPath;
    }

    /**
     * Upload an extracted historical image (battle map, artifact, photograph) to Supabase Storage.
     * By default, uploads to the staging directory ('staging/historical-images/') subject to TTL cleanup.
     */
    public String uploadHistoricalImage(byte[] imageBytes, String extension) {
        return uploadHistoricalImage(imageBytes, extension, true);
    }

    /**
     * Upload historical image to staging or published directory.
     *
     * @param imageBytes image binary data
     * @param extension file extension (e.g., "jpg", "png")
     * @param isStaging true if saving temporarily in staging folder, false if published directly
     * @return Public URL of the uploaded image
     */
    public String uploadHistoricalImage(byte[] imageBytes, String extension, boolean isStaging) {
        if (imageBytes == null || imageBytes.length == 0) {
            return null;
        }
        String ext = (extension != null && !extension.isBlank()) ? extension.toLowerCase(Locale.ROOT) : "jpg";
        String filename = UUID.randomUUID() + "." + ext;
        String folder = isStaging ? "staging/historical-images/" : "published/historical-images/";
        String objectPath = folder + filename;
        String contentType = ext.equals("png") ? "image/png" : "image/jpeg";

        try {
            uploadFile(objectPath, new java.io.ByteArrayInputStream(imageBytes), contentType, imageBytes.length);
            String relativeUrl = (isStaging ? "/historical-images/staging/" : "/historical-images/published/") + filename;
            log.info("Historical image uploaded to Supabase (isStaging={}): {}", isStaging, relativeUrl);
            return relativeUrl;
        } catch (Exception ex) {
            log.warn("Failed to upload historical image to Supabase: {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Move an object inside Supabase Storage using /storage/v1/object/move.
     */
    public void moveFile(String sourceKey, String destinationKey) {
        try {
            String cleanSource = sourceKey.startsWith(bucket + "/") 
                    ? sourceKey.substring(bucket.length() + 1) 
                    : sourceKey;
            String cleanDest = destinationKey.startsWith(bucket + "/") 
                    ? destinationKey.substring(bucket.length() + 1) 
                    : destinationKey;

            Map<String, String> body = Map.of(
                    "bucketId", bucket,
                    "sourceKey", cleanSource,
                    "destinationKey", cleanDest
            );

            restClient.post()
                    .uri("/storage/v1/object/move")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Moved Supabase object from {} to {}", cleanSource, cleanDest);
        } catch (Exception ex) {
            log.warn("Could not move file in Supabase from {} to {}: {}", sourceKey, destinationKey, ex.getMessage());
        }
    }

    /**
     * Commit staging images referenced in markdown content.
     * Moves each referenced file from 'staging/historical-images/' to 'published/historical-images/'
     * and rewrites the content URLs accordingly.
     * Images deleted by the user from the draft remain in staging and are purged by TTL.
     */
    public String commitStagingImages(String content) {
        if (content == null || content.isBlank()
                || (!content.contains("staging/historical-images/") && !content.contains("/historical-images/staging/"))) {
            return content;
        }

        Pattern pattern = Pattern.compile("(?:staging/historical-images/|/historical-images/staging/)([a-zA-Z0-9_.-]+)");
        Matcher matcher = pattern.matcher(content);
        Set<String> filenames = new HashSet<>();
        while (matcher.find()) {
            filenames.add(matcher.group(1));
        }

        for (String filename : filenames) {
            String sourceKey = "staging/historical-images/" + filename;
            String destinationKey = "published/historical-images/" + filename;
            moveFile(sourceKey, destinationKey);
        }

        return content
                .replace("/historical-images/staging/", "/historical-images/published/")
                .replace("staging/historical-images/", "published/historical-images/");
    }

    /**
     * Best-effort cleanup of a document's PDF file and all extracted historical images
     * (both staging and published) referenced in its markdown content.
     * Never throws so database deletion is not aborted if an object was already removed.
     */
    public void deleteDocumentStorageAssets(String fileUrl, String content) {
        if (fileUrl != null && !fileUrl.isBlank()) {
            safeDeleteStorageObject(fileUrl);
        }
        if (content != null && !content.isBlank()) {
            Pattern imgPattern = Pattern.compile("(?:(staging|published)/historical-images/|/historical-images/(staging|published)/)([a-zA-Z0-9_.-]+)");
            Matcher matcher = imgPattern.matcher(content);
            Set<String> paths = new HashSet<>();
            while (matcher.find()) {
                String folder = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
                String filename = matcher.group(3);
                paths.add(folder + "/historical-images/" + filename);
            }
            for (String path : paths) {
                safeDeleteStorageObject(path);
            }
        }
    }

    /**
     * Best-effort cleanup for a single URL or storage path if it belongs to this Supabase bucket.
     */
    public void safeDeleteStorageObject(String urlOrPath) {
        if (urlOrPath == null || urlOrPath.isBlank()) {
            return;
        }
        try {
            String path = urlOrPath.trim();
            String publicPrefix = "/storage/v1/object/public/" + bucket + "/";
            int pubIdx = path.indexOf(publicPrefix);
            if (pubIdx != -1) {
                path = path.substring(pubIdx + publicPrefix.length());
            } else if (path.startsWith("http://") || path.startsWith("https://")) {
                // External URL not stored in our documents bucket
                return;
            }
            int queryIdx = path.indexOf('?');
            if (queryIdx != -1) {
                path = path.substring(0, queryIdx);
            }
            deleteFile(path);
            log.info("Deleted Supabase storage asset: {}", path);
        } catch (Exception ex) {
            log.warn("Best-effort Supabase delete skipped/failed for {}: {}", urlOrPath, ex.getMessage());
        }
    }

    private record PresignedUploadRequest(@JsonProperty("expiresIn") long expiresIn) {
    }

    private record PresignedUploadResponse(
            @JsonProperty("url") String url,
            @JsonProperty("signedUrl") String signedUrl
    ) {
        public String getEffectiveUrl() {
            return url != null ? url : signedUrl;
        }
    }

    public record SupabaseStorageItem(
            @JsonProperty("name") String name,
            @JsonProperty("id") String id,
            @JsonProperty("created_at") String createdAt,
            @JsonProperty("updated_at") String updatedAt
    ) {
    }

    private record SupabaseListRequest(
            @JsonProperty("prefix") String prefix,
            @JsonProperty("limit") int limit,
            @JsonProperty("sortBy") Map<String, String> sortBy
    ) {
    }

    /**
     * Purge expired staging objects in a given folder prefix older than maxAge.
     * Direct deletion on Supabase storage tables is locked by 'storage.protect_delete()',
     * so this uses the Supabase Storage List API followed by Storage Delete API.
     *
     * @param prefix folder prefix (e.g. "staging/historical-images")
     * @param maxAge maximum age duration before an object is considered expired
     * @return number of purged objects
     */
    public int purgeExpiredStagingObjects(String prefix, Duration maxAge) {
        if (prefix == null || prefix.isBlank() || maxAge == null) {
            return 0;
        }
        String cleanPrefix = prefix.replaceAll("^/+", "").replaceAll("/+$", "");
        Instant expirationThreshold = Instant.now().minus(maxAge);
        int purgedCount = 0;

        try {
            SupabaseListRequest request = new SupabaseListRequest(
                    cleanPrefix,
                    100,
                    Map.of("column", "created_at", "order", "asc")
            );

            SupabaseStorageItem[] items = restClient.post()
                    .uri("/storage/v1/object/list/" + bucket)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(SupabaseStorageItem[].class);

            if (items == null || items.length == 0) {
                log.debug("No staging objects found under prefix '{}'", cleanPrefix);
                return 0;
            }

            for (SupabaseStorageItem item : items) {
                if (item.name() == null || item.name().isBlank() || ".emptyFolderPlaceholder".equalsIgnoreCase(item.name())) {
                    continue;
                }
                if (item.createdAt() == null || item.createdAt().isBlank()) {
                    continue;
                }

                try {
                    Instant createdAt = parseSupabaseTimestamp(item.createdAt());
                    if (createdAt.isBefore(expirationThreshold)) {
                        String filePath = item.name().startsWith(cleanPrefix)
                                ? item.name()
                                : cleanPrefix + "/" + item.name();
                        deleteFile(filePath);
                        purgedCount++;
                        log.info("Purged expired staging object: {} (created at {})", filePath, createdAt);
                    }
                } catch (Exception ex) {
                    log.warn("Failed to evaluate/delete staging object '{}': {}", item.name(), ex.getMessage());
                }
            }
        } catch (Exception ex) {
            log.warn("Failed to list/purge staging objects under prefix '{}': {}", cleanPrefix, ex.getMessage());
        }

        if (purgedCount > 0) {
            log.info("Finished staging cleanup for '{}': purged {} expired objects", cleanPrefix, purgedCount);
        }
        return purgedCount;
    }

    private Instant parseSupabaseTimestamp(String timestamp) {
        try {
            return Instant.parse(timestamp);
        } catch (Exception e) {
            return OffsetDateTime.parse(timestamp).toInstant();
        }
    }
}
