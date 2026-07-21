package com.historytalk.service.user;

import com.historytalk.dto.document.SignedViewUrlResponse;
import com.historytalk.entity.enums.MediaType;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.UserRepository;
import com.historytalk.service.document.SupabaseDocumentStorageService;
import com.historytalk.service.media.DirectBinaryUploadStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAvatarServiceImpl implements UserAvatarService {

    private static final long VIEW_URL_EXPIRES_IN_SECONDS = 3600;
    private static final long MAX_AVATAR_SIZE_BYTES = 5 * 1024 * 1024L; // 5MB for avatars

    private final UserRepository userRepository;
    private final SupabaseDocumentStorageService supabaseDocumentStorageService;
    private final DirectBinaryUploadStrategy directBinaryUploadStrategy;

    @Override
    @Transactional
    public SignedViewUrlResponse uploadAvatarDirect(
            String userId,
            MultipartFile file,
            String mediaType,
            Integer width,
            Integer height,
            String currentUserId,
            String userRole) {

        UUID targetUserId = UUID.fromString(userId);
        validateOwnership(targetUserId, currentUserId, userRole);

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + userId));

        // Validate file
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("File không được để trống");
        }

        // Validate media type
        MediaType mediaTypeEnum;
        try {
            mediaTypeEnum = MediaType.valueOf(mediaType);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Media type không hợp lệ: " + mediaType);
        }

        if (mediaTypeEnum != MediaType.IMAGE_2D) {
            throw new InvalidRequestException("Direct binary upload chỉ hỗ trợ IMAGE_2D");
        }

        // Validate content type
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidRequestException("File phải là ảnh");
        }

        // Validate file size
        if (file.getSize() > MAX_AVATAR_SIZE_BYTES) {
            throw new InvalidRequestException("Kích thước avatar không được vượt quá 5MB");
        }

        // Generate storage path
        String storagePath = generateAvatarStoragePath(targetUserId, file.getOriginalFilename());

        try {
            // Upload file using DirectBinaryUploadStrategy
            directBinaryUploadStrategy.uploadMultipartFile(storagePath, file, contentType, "IMAGE_2D");

            // Update user avatar URL
            user.setAvatarUrl(storagePath);
            userRepository.save(user);

            // Generate signed view URL
            String viewUrl = supabaseDocumentStorageService.createSignedUrl(
                    storagePath, VIEW_URL_EXPIRES_IN_SECONDS);

            log.info("Direct binary upload completed for user {} avatar with storage path {}", userId, storagePath);

            return SignedViewUrlResponse.builder()
                    .viewUrl(viewUrl)
                    .thumbnailUrl(null)
                    .expiresIn(VIEW_URL_EXPIRES_IN_SECONDS)
                    .build();

        } catch (IOException e) {
            throw new InvalidRequestException("Không thể đọc file: " + e.getMessage());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SignedViewUrlResponse generateAvatarViewUrl(String userId, String currentUserId) {
        UUID targetUserId = UUID.fromString(userId);

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + userId));

        if (user.getAvatarUrl() == null || user.getAvatarUrl().isEmpty()) {
            throw new ResourceNotFoundException("Người dùng chưa có avatar");
        }

        String viewUrl = supabaseDocumentStorageService.createSignedUrl(
                user.getAvatarUrl(), VIEW_URL_EXPIRES_IN_SECONDS);

        return SignedViewUrlResponse.builder()
                .viewUrl(viewUrl)
                .thumbnailUrl(null)
                .expiresIn(VIEW_URL_EXPIRES_IN_SECONDS)
                .build();
    }

    @Override
    @Transactional
    public void deleteAvatar(String userId, String currentUserId, String userRole) {
        UUID targetUserId = UUID.fromString(userId);
        validateOwnership(targetUserId, currentUserId, userRole);

        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + userId));

        if (user.getAvatarUrl() != null && !user.getAvatarUrl().isEmpty()) {
            supabaseDocumentStorageService.deleteFile(user.getAvatarUrl());
            user.setAvatarUrl(null);
            userRepository.save(user);

            log.info("Deleted avatar for user {}", userId);
        }
    }

    private void validateOwnership(UUID targetUserId, String currentUserId, String userRole) {
        if (!isAdmin(userRole) && !targetUserId.toString().equals(currentUserId)) {
            throw new InvalidRequestException("Bạn không có quyền thực hiện hành động này");
        }
    }

    private String generateAvatarStoragePath(UUID userId, String fileName) {
        return "users/%s/avatar/%s".formatted(userId, fileName);
    }

    private boolean isAdmin(String userRole) {
        return userRole != null && (
                "ADMIN".equalsIgnoreCase(userRole)
                        || "SYSTEM_ADMIN".equalsIgnoreCase(userRole)
                        || "CONTENT_ADMIN".equalsIgnoreCase(userRole)
        );
    }
}
