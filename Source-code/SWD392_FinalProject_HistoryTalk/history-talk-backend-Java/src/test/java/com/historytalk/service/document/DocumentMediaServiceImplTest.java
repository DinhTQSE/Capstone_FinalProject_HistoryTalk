package com.historytalk.service.document;

import com.historytalk.dto.document.DocumentMediaMetadataResponse;
import com.historytalk.dto.document.MediaUploadConfirmationRequest;
import com.historytalk.dto.document.MediaUploadRequest;
import com.historytalk.dto.document.PresignedUploadUrlResponse;
import com.historytalk.dto.document.SignedViewUrlResponse;
import com.historytalk.entity.document.Document;
import com.historytalk.entity.document.DocumentMediaMetadata;
import com.historytalk.entity.enums.DocumentType;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.entity.enums.MediaType;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.DocumentMediaMetadataRepository;
import com.historytalk.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentMediaServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentMediaMetadataRepository mediaMetadataRepository;

    @Mock
    private SupabaseDocumentStorageService supabaseDocumentStorageService;

    @InjectMocks
    private DocumentMediaServiceImpl documentMediaService;

    private UUID docId;
    private Document document;
    private MediaUploadRequest uploadRequest;
    private MediaUploadConfirmationRequest confirmationRequest;

    @BeforeEach
    void setUp() {
        docId = UUID.randomUUID();
        document = Document.builder()
                .docId(docId)
                .entityId(UUID.randomUUID())
                .entityType(EntityType.CHARACTER)
                .title("Test Document")
                .documentType(DocumentType.TEXT)
                .build();

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
    void generatePresignedUploadUrl_Success() {
        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(supabaseDocumentStorageService.generatePresignedUploadUrl(anyString(), anyString(), anyLong()))
                .thenReturn("https://supabase.com/storage/presigned-url");

        PresignedUploadUrlResponse response = documentMediaService.generatePresignedUploadUrl(
                docId.toString(), uploadRequest, "user123", "CONTENT_ADMIN");

        assertNotNull(response);
        assertEquals("https://supabase.com/storage/presigned-url", response.getUploadUrl());
        assertEquals(300L, response.getExpiresIn());
        assertNotNull(response.getUploadId());
        verify(documentRepository).findById(docId);
        verify(supabaseDocumentStorageService).generatePresignedUploadUrl(anyString(), anyString(), anyLong());
    }

    @Test
    void generatePresignedUploadUrl_DocumentNotFound() {
        when(documentRepository.findById(docId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                documentMediaService.generatePresignedUploadUrl(
                        docId.toString(), uploadRequest, "user123", "CONTENT_ADMIN"));

        verify(documentRepository).findById(docId);
        verify(supabaseDocumentStorageService, never()).generatePresignedUploadUrl(anyString(), anyString(), anyLong());
    }

    @Test
    void generatePresignedUploadUrl_UnauthorizedUser() {
        assertThrows(InvalidRequestException.class, () ->
                documentMediaService.generatePresignedUploadUrl(
                        docId.toString(), uploadRequest, "user123", "USER"));

        verify(documentRepository, never()).findById(any(UUID.class));
        verify(supabaseDocumentStorageService, never()).generatePresignedUploadUrl(anyString(), anyString(), anyLong());
    }

    @Test
    void generatePresignedUploadUrl_FileSizeExceedsLimit() {
        uploadRequest.setFileSizeBytes(20 * 1024 * 1024L); // 20MB exceeds 10MB limit
        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));

        assertThrows(InvalidRequestException.class, () ->
                documentMediaService.generatePresignedUploadUrl(
                        docId.toString(), uploadRequest, "user123", "CONTENT_ADMIN"));

        verify(documentRepository).findById(docId);
        verify(supabaseDocumentStorageService, never()).generatePresignedUploadUrl(anyString(), anyString(), anyLong());
    }

    @Test
    void generatePresignedUploadUrl_InvalidContentType() {
        uploadRequest.setContentType("application/pdf");
        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));

        assertThrows(InvalidRequestException.class, () ->
                documentMediaService.generatePresignedUploadUrl(
                        docId.toString(), uploadRequest, "user123", "CONTENT_ADMIN"));

        verify(documentRepository).findById(docId);
        verify(supabaseDocumentStorageService, never()).generatePresignedUploadUrl(anyString(), anyString(), anyLong());
    }

    @Test
    void confirmUpload_Success() {
        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenReturn(document);
        when(mediaMetadataRepository.save(any(DocumentMediaMetadata.class))).thenAnswer(invocation -> {
            DocumentMediaMetadata metadata = invocation.getArgument(0);
            metadata.setMetadataId(UUID.randomUUID());
            return metadata;
        });

        DocumentMediaMetadataResponse response = documentMediaService.confirmUpload(
                docId.toString(), confirmationRequest, "user123", "CONTENT_ADMIN");

        assertNotNull(response);
        assertEquals(MediaType.IMAGE_2D.name(), response.getMediaType());
        assertEquals("jpeg", response.getFileFormat());
        assertEquals(1920, response.getWidth());
        assertEquals(1080, response.getHeight());
        verify(documentRepository).findById(docId);
        verify(documentRepository).save(any(Document.class));
        verify(mediaMetadataRepository).save(any(DocumentMediaMetadata.class));
    }

    @Test
    void confirmUpload_3DModel() {
        confirmationRequest.setContentType("model/gltf-binary");
        confirmationRequest.setExtendedMetadata("{\"polygonCount\": 10000}");
        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenReturn(document);
        when(mediaMetadataRepository.save(any(DocumentMediaMetadata.class))).thenAnswer(invocation -> {
            DocumentMediaMetadata metadata = invocation.getArgument(0);
            metadata.setMetadataId(UUID.randomUUID());
            return metadata;
        });

        DocumentMediaMetadataResponse response = documentMediaService.confirmUpload(
                docId.toString(), confirmationRequest, "user123", "CONTENT_ADMIN");

        assertNotNull(response);
        assertEquals(MediaType.MODEL_3D.name(), response.getMediaType());
        assertEquals("glb", response.getFileFormat());
        assertEquals("{\"polygonCount\": 10000}", response.getExtendedMetadata());
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    void confirmUpload_StoragePathMismatch() {
        confirmationRequest.setStoragePath("documents/character/other-id/test.jpg");
        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));

        assertThrows(InvalidRequestException.class, () ->
                documentMediaService.confirmUpload(
                        docId.toString(), confirmationRequest, "user123", "CONTENT_ADMIN"));

        verify(documentRepository).findById(docId);
        verify(documentRepository, never()).save(any(Document.class));
        verify(mediaMetadataRepository, never()).save(any(DocumentMediaMetadata.class));
    }

    @Test
    void generateSignedViewUrl_Success() {
        DocumentMediaMetadata metadata = DocumentMediaMetadata.builder()
                .metadataId(UUID.randomUUID())
                .documentId(docId)
                .mediaType(MediaType.IMAGE_2D)
                .storagePath("documents/character/" + docId + "/test.jpg")
                .build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(mediaMetadataRepository.findByDocumentId(docId)).thenReturn(Optional.of(metadata));
        when(supabaseDocumentStorageService.createSignedUrl(anyString(), anyLong()))
                .thenReturn("https://supabase.com/storage/signed-url");

        SignedViewUrlResponse response = documentMediaService.generateSignedViewUrl(
                docId.toString(), 300, 300, "user123", "USER");

        assertNotNull(response);
        assertEquals("https://supabase.com/storage/signed-url", response.getViewUrl());
        assertNotNull(response.getThumbnailUrl());
        assertTrue(response.getThumbnailUrl().contains("width=300"));
        assertTrue(response.getThumbnailUrl().contains("height=300"));
        assertEquals(3600L, response.getExpiresIn());
    }

    @Test
    void generateSignedViewUrl_3DModel_NoThumbnail() {
        DocumentMediaMetadata metadata = DocumentMediaMetadata.builder()
                .metadataId(UUID.randomUUID())
                .documentId(docId)
                .mediaType(MediaType.MODEL_3D)
                .storagePath("documents/character/" + docId + "/model.glb")
                .build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(mediaMetadataRepository.findByDocumentId(docId)).thenReturn(Optional.of(metadata));
        when(supabaseDocumentStorageService.createSignedUrl(anyString(), anyLong()))
                .thenReturn("https://supabase.com/storage/signed-url");

        SignedViewUrlResponse response = documentMediaService.generateSignedViewUrl(
                docId.toString(), 300, 300, "user123", "USER");

        assertNotNull(response);
        assertEquals("https://supabase.com/storage/signed-url", response.getViewUrl());
        assertNull(response.getThumbnailUrl());
    }

    @Test
    void generateSignedViewUrl_MetadataNotFound() {
        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(mediaMetadataRepository.findByDocumentId(docId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                documentMediaService.generateSignedViewUrl(
                        docId.toString(), 300, 300, "user123", "USER"));

        verify(documentRepository).findById(docId);
        verify(mediaMetadataRepository).findByDocumentId(docId);
        verify(supabaseDocumentStorageService, never()).createSignedUrl(anyString(), anyLong());
    }

    @Test
    void deleteMedia_Success() {
        DocumentMediaMetadata metadata = DocumentMediaMetadata.builder()
                .metadataId(UUID.randomUUID())
                .documentId(docId)
                .mediaType(MediaType.IMAGE_2D)
                .storagePath("documents/character/" + docId + "/test.jpg")
                .build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(mediaMetadataRepository.findByDocumentId(docId)).thenReturn(Optional.of(metadata));
        when(documentRepository.save(any(Document.class))).thenReturn(document);

        documentMediaService.deleteMedia(docId.toString(), "user123", "CONTENT_ADMIN");

        verify(supabaseDocumentStorageService).deleteFile(metadata.getStoragePath());
        verify(mediaMetadataRepository).delete(metadata);
        verify(documentRepository).save(any(Document.class));
    }

    @Test
    void deleteMedia_UnauthorizedUser() {
        assertThrows(InvalidRequestException.class, () ->
                documentMediaService.deleteMedia(docId.toString(), "user123", "USER"));

        verify(documentRepository, never()).findById(any(UUID.class));
        verify(supabaseDocumentStorageService, never()).deleteFile(anyString());
        verify(mediaMetadataRepository, never()).delete(any(DocumentMediaMetadata.class));
    }
}
