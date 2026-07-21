package com.historytalk.controller.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.document.MediaUploadConfirmationRequest;
import com.historytalk.dto.document.MediaUploadRequest;
import com.historytalk.dto.document.PresignedUploadUrlResponse;
import com.historytalk.dto.document.SignedViewUrlResponse;
import com.historytalk.entity.document.Document;
import com.historytalk.entity.document.DocumentMediaMetadata;
import com.historytalk.entity.enums.DocumentType;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.repository.DocumentMediaMetadataRepository;
import com.historytalk.repository.DocumentRepository;
import com.historytalk.service.document.DocumentMediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentMediaController.class)
class DocumentMediaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DocumentMediaService documentMediaService;

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private DocumentMediaMetadataRepository mediaMetadataRepository;

    private UUID docId;
    private MediaUploadRequest uploadRequest;
    private MediaUploadConfirmationRequest confirmationRequest;

    @BeforeEach
    void setUp() {
        docId = UUID.randomUUID();

        uploadRequest = MediaUploadRequest.builder()
                .fileName("test.jpg")
                .contentType("image/jpeg")
                .fileSizeBytes(5 * 1024 * 1024L)
                .mediaType("IMAGE_2D")
                .build();

        confirmationRequest = MediaUploadConfirmationRequest.builder()
                .uploadId(UUID.randomUUID().toString())
                .storagePath("documents/character/" + docId + "/test.jpg")
                .contentType("image/jpeg")
                .width(1920)
                .height(1080)
                .build();
    }

    @Test
    @WithMockUser(roles = {"CONTENT_ADMIN"})
    void getUploadUrl_Success() throws Exception {
        PresignedUploadUrlResponse presignedResponse = PresignedUploadUrlResponse.builder()
                .uploadUrl("https://supabase.com/storage/presigned-url")
                .storagePath("documents/character/" + docId + "/test.jpg")
                .expiresIn(300L)
                .uploadId(UUID.randomUUID().toString())
                .build();

        when(documentMediaService.generatePresignedUploadUrl(
                eq(docId.toString()), any(MediaUploadRequest.class), anyString(), anyString()))
                .thenReturn(presignedResponse);

        mockMvc.perform(post("/api/v1/documents/{docId}/media/upload-url", docId)
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
    @WithMockUser(roles = {"CONTENT_ADMIN"})
    void getUploadUrl_InvalidRequest() throws Exception {
        uploadRequest.setFileName(""); // Invalid: empty filename

        mockMvc.perform(post("/api/v1/documents/{docId}/media/upload-url", docId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(uploadRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = {"CONTENT_ADMIN"})
    void confirmUpload_Success() throws Exception {
        var metadataResponse = com.historytalk.dto.document.DocumentMediaMetadataResponse.builder()
                .metadataId(UUID.randomUUID().toString())
                .documentId(docId.toString())
                .mediaType(com.historytalk.entity.enums.MediaType.IMAGE_2D.name())
                .fileFormat("jpg")
                .width(1920)
                .height(1080)
                .build();

        when(documentMediaService.confirmUpload(
                eq(docId.toString()), any(MediaUploadConfirmationRequest.class), anyString(), anyString()))
                .thenReturn(metadataResponse);

        mockMvc.perform(post("/api/v1/documents/{docId}/media/confirm", docId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmationRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mediaType").value("IMAGE_2D"))
                .andExpect(jsonPath("$.data.fileFormat").value("jpg"))
                .andExpect(jsonPath("$.data.width").value(1920))
                .andExpect(jsonPath("$.data.height").value(1080));
    }

    @Test
    @WithMockUser(roles = {"CONTENT_ADMIN"})
    void confirmUpload_InvalidRequest() throws Exception {
        confirmationRequest.setUploadId(""); // Invalid: empty uploadId

        mockMvc.perform(post("/api/v1/documents/{docId}/media/confirm", docId)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmationRequest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void getViewUrl_Success() throws Exception {
        SignedViewUrlResponse viewUrlResponse = SignedViewUrlResponse.builder()
                .viewUrl("https://supabase.com/storage/signed-url")
                .thumbnailUrl("https://supabase.com/storage/thumbnail-url")
                .expiresIn(3600L)
                .build();

        when(documentMediaService.generateSignedViewUrl(
                eq(docId.toString()), eq(300), eq(300), anyString(), anyString()))
                .thenReturn(viewUrlResponse);

        mockMvc.perform(get("/api/v1/documents/{docId}/media/view-url", docId)
                        .param("thumbnailWidth", "300")
                        .param("thumbnailHeight", "300"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.viewUrl").value("https://supabase.com/storage/signed-url"))
                .andExpect(jsonPath("$.data.thumbnailUrl").value("https://supabase.com/storage/thumbnail-url"))
                .andExpect(jsonPath("$.data.expiresIn").value(3600));
    }

    @Test
    @WithMockUser(roles = {"USER"})
    void getViewUrl_WithoutThumbnail() throws Exception {
        SignedViewUrlResponse viewUrlResponse = SignedViewUrlResponse.builder()
                .viewUrl("https://supabase.com/storage/signed-url")
                .thumbnailUrl(null)
                .expiresIn(3600L)
                .build();

        when(documentMediaService.generateSignedViewUrl(
                eq(docId.toString()), any(), any(), anyString(), anyString()))
                .thenReturn(viewUrlResponse);

        mockMvc.perform(get("/api/v1/documents/{docId}/media/view-url", docId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.viewUrl").value("https://supabase.com/storage/signed-url"))
                .andExpect(jsonPath("$.data.thumbnailUrl").isEmpty());
    }

    @Test
    @WithMockUser(roles = {"CONTENT_ADMIN"})
    void deleteMedia_Success() throws Exception {
        mockMvc.perform(delete("/api/v1/documents/{docId}/media", docId)
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }
}
