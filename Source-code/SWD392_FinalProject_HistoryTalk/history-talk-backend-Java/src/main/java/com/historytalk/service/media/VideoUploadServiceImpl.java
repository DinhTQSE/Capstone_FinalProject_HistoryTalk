package com.historytalk.service.media;

import com.historytalk.exception.InvalidRequestException;
import com.historytalk.service.document.SupabaseDocumentStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * Stores standalone videos in the existing {@code documents} bucket:
 * <pre>
 *   videos/{userId}/{uuid}.{ext}
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VideoUploadServiceImpl implements VideoUploadService {

    private static final String PATH_PREFIX = "videos";

    private final SupabaseDocumentStorageService supabaseDocumentStorageService;

    @Value("${supabase.url}")
    private String supabaseUrl;

    @Value("${supabase.storage.bucket:documents}")
    private String bucket;

    @Override
    public String uploadVideo(MultipartFile file, String userId) {
        String contentType = VideoFileValidator.validateAndResolveContentType(file);
        String owner = userId != null ? UUID.fromString(userId).toString() : "anonymous";
        String objectPath = PATH_PREFIX + "/" + owner + "/" + UUID.randomUUID() + "."
                + VideoFileValidator.extensionFor(contentType);

        try {
            supabaseDocumentStorageService.uploadFile(objectPath, file.getInputStream(), contentType, file.getSize());
        } catch (IOException ex) {
            throw new InvalidRequestException("Không thể đọc file video: " + ex.getMessage());
        }

        String cleanBase = supabaseUrl.replaceAll("/+$", "");
        String publicUrl = cleanBase + "/storage/v1/object/public/" + bucket + "/" + objectPath;
        log.info("Video uploaded: path={} by userId={}", objectPath, userId);
        return publicUrl;
    }
}
