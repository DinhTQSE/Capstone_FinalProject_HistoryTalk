package com.historytalk.controller.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.document.MediaUploadConfirmationRequest;
import com.historytalk.dto.document.MediaUploadRequest;
import com.historytalk.dto.document.PresignedUploadUrlResponse;
import com.historytalk.dto.document.SignedViewUrlResponse;
import com.historytalk.entity.user.User;
import com.historytalk.repository.UserRepository;
import com.historytalk.service.user.UserAvatarService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(UserAvatarController.class)
class UserAvatarControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserAvatarService userAvatarService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private UserDetailsService userDetailsService;

    private UUID userId;
    private MediaUploadRequest uploadRequest;
    private MediaUploadConfirmationRequest confirmationRequest;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();

        uploadRequest = MediaUploadRequest.builder()
                .fileName("avatar.jpg")
                .contentType("image/jpeg")
                .fileSizeBytes(2 * 1024 * 1024L)
                .mediaType("IMAGE_2D")
                .build();

        confirmationRequest = MediaUploadConfirmationRequest.builder()
                .uploadId(UUID.randomUUID().toString())
                .storagePath("users/" + userId + "/avatar/avatar.jpg")
                .contentType("image/jpeg")
                .width(200)
                .height(200)
                .build();

        userDetails = org.springframework.security.core.userdetails.User
                .withUsername(userId.toString())
                .password("password")
                .authorities(new SimpleGrantedAuthority("ROLE_USER"))
                .build();
    }

    @Test
    void generateAvatarUploadUrl_Success() throws Exception {
        PresignedUploadUrlResponse presignedResponse = PresignedUploadUrlResponse.builder()
                .uploadUrl("https://supabase.com/storage/presigned-url")
                .storagePath("users/" + userId + "/avatar/avatar.jpg")
                .expiresIn(300L)
                .uploadId(UUID.randomUUID().toString())
                .build();

        when(userAvatarService.generateAvatarUploadUrl(
                anyString(), any(MediaUploadRequest.class), anyString(), anyString()))
                .thenReturn(presignedResponse);

        mockMvc.perform(post("/api/v1/users/{userId}/avatar/upload-url", userId)
                        .with(user(userDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uploadRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.uploadUrl").value("https://supabase.com/storage/presigned-url"))
                .andExpect(jsonPath("$.data.expiresIn").value(300))
                .andExpect(jsonPath("$.data.uploadId").exists());
    }

    @Test
    void generateAvatarUploadUrl_InvalidRequest() throws Exception {
        uploadRequest.setFileName(""); // Invalid: empty filename

        mockMvc.perform(post("/api/v1/users/{userId}/avatar/upload-url", userId)
                        .with(user(userDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uploadRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void confirmAvatarUpload_Success() throws Exception {
        SignedViewUrlResponse viewUrlResponse = SignedViewUrlResponse.builder()
                .viewUrl("https://supabase.com/storage/signed-url")
                .thumbnailUrl(null)
                .expiresIn(3600L)
                .build();

        when(userAvatarService.confirmAvatarUpload(
                anyString(), any(MediaUploadConfirmationRequest.class), anyString(), anyString()))
                .thenReturn(viewUrlResponse);

        mockMvc.perform(post("/api/v1/users/{userId}/avatar/confirm", userId)
                        .with(user(userDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.viewUrl").value("https://supabase.com/storage/signed-url"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600));
    }

    @Test
    void confirmAvatarUpload_InvalidRequest() throws Exception {
        confirmationRequest.setUploadId(""); // Invalid: empty uploadId

        mockMvc.perform(post("/api/v1/users/{userId}/avatar/confirm", userId)
                        .with(user(userDetails))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmationRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void generateAvatarViewUrl_Success() throws Exception {
        SignedViewUrlResponse viewUrlResponse = SignedViewUrlResponse.builder()
                .viewUrl("https://supabase.com/storage/signed-url")
                .thumbnailUrl(null)
                .expiresIn(3600L)
                .build();

        when(userAvatarService.generateAvatarViewUrl(anyString(), anyString()))
                .thenReturn(viewUrlResponse);

        mockMvc.perform(get("/api/v1/users/{userId}/avatar/view-url", userId)
                        .with(user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.viewUrl").value("https://supabase.com/storage/signed-url"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600));
    }

    @Test
    void deleteAvatar_Success() throws Exception {
        mockMvc.perform(delete("/api/v1/users/{userId}/avatar", userId)
                        .with(user(userDetails))
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }
}
