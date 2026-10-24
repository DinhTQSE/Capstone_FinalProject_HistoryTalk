package com.historytalk.service.media;

import org.springframework.web.multipart.MultipartFile;

/**
 * Uploads a standalone video (not yet bound to an entity) and returns its public URL,
 * so a create/edit form can fill {@code videoUrl} before the Historical Context exists.
 * <p>
 * To attach a video to an existing context in one step (with metadata + replacement of the
 * previous video), use {@link MediaService#uploadDirectBinary} with {@code mediaType=VIDEO}.
 */
public interface VideoUploadService {

    /**
     * @param file   MP4, WebM, Ogg or MOV; maximum 50 MB.
     * @param userId Caller's user ID (used as storage folder and for audit).
     * @return Permanent public Supabase URL to the uploaded video.
     * @throws com.historytalk.exception.InvalidRequestException if the file fails validation.
     */
    String uploadVideo(MultipartFile file, String userId);
}
