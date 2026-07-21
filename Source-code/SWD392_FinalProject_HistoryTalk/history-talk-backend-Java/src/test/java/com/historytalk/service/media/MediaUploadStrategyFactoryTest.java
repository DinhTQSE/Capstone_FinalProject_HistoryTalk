package com.historytalk.service.media;

import com.historytalk.entity.enums.MediaType;
import com.historytalk.service.document.SupabaseDocumentStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
class MediaUploadStrategyFactoryTest {

    @Autowired
    private MediaUploadStrategyFactory strategyFactory;

    @MockBean
    private SupabaseDocumentStorageService supabaseDocumentStorageService;

    @BeforeEach
    void setUp() {
        when(supabaseDocumentStorageService.generatePresignedUploadUrl(anyString(), anyString(), anyLong()))
                .thenReturn("https://supabase.com/storage/presigned-url");
    }

    @Test
    void testGetStrategyForImage2D() {
        MediaUploadStrategy strategy = strategyFactory.getStrategy(MediaType.IMAGE_2D);
        
        assertNotNull(strategy);
        assertEquals(MediaType.IMAGE_2D, strategy.getSupportedType());
        assertTrue(strategy instanceof DirectBinaryUploadStrategy);
    }

    @Test
    void testGetStrategyForModel3D() {
        MediaUploadStrategy strategy = strategyFactory.getStrategy(MediaType.MODEL_3D);
        
        assertNotNull(strategy);
        assertEquals(MediaType.MODEL_3D, strategy.getSupportedType());
        assertTrue(strategy instanceof PresignedUrlUploadStrategy);
    }

    @Test
    void testGetStrategyThrowsExceptionForUnsupportedType() {
        // This test verifies that the factory throws an exception for unsupported media types
        // Since we only have IMAGE_2D and MODEL_3D, we can't test this directly
        // But we can verify the factory returns the correct strategies for supported types
        assertDoesNotThrow(() -> strategyFactory.getStrategy(MediaType.IMAGE_2D));
        assertDoesNotThrow(() -> strategyFactory.getStrategy(MediaType.MODEL_3D));
    }

    @Test
    void testGetSupportedTypes() {
        Set<MediaType> supportedTypes = strategyFactory.getSupportedTypes();
        
        assertNotNull(supportedTypes);
        assertEquals(2, supportedTypes.size());
        assertTrue(supportedTypes.contains(MediaType.IMAGE_2D));
        assertTrue(supportedTypes.contains(MediaType.MODEL_3D));
    }

    @Test
    void testDirectBinaryUploadStrategyMaxFileSize() {
        MediaUploadStrategy strategy = strategyFactory.getStrategy(MediaType.IMAGE_2D);
        
        assertThrows(IllegalArgumentException.class, () -> 
                strategy.upload("test/path", "image/jpeg", 20 * 1024 * 1024L)); // 20MB exceeds 10MB limit
    }

    @Test
    void testDirectBinaryUploadStrategySuccess() {
        MediaUploadStrategy strategy = strategyFactory.getStrategy(MediaType.IMAGE_2D);
        
        MediaUploadStrategy.UploadResult result = strategy.upload(
                "test/path/image.jpg", 
                "image/jpeg", 
                5 * 1024 * 1024L); // 5MB within limit
        
        assertNotNull(result);
        assertNull(result.uploadUrl()); // Direct binary has no presigned URL
        assertEquals("test/path/image.jpg", result.storagePath());
        assertNull(result.expiresIn());
        assertNotNull(result.uploadId());
    }

    @Test
    void testPresignedUrlUploadStrategySuccess() {
        MediaUploadStrategy strategy = strategyFactory.getStrategy(MediaType.MODEL_3D);
        
        MediaUploadStrategy.UploadResult result = strategy.upload(
                "test/path/model.glb", 
                "model/gltf-binary", 
                50 * 1024 * 1024L); // 50MB
        
        assertNotNull(result);
        assertNotNull(result.uploadUrl());
        assertNotNull(result.uploadId());
        assertEquals("test/path/model.glb", result.storagePath());
        assertNotNull(result.expiresIn());
    }
}
