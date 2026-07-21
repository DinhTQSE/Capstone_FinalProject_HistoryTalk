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
import com.historytalk.repository.DocumentMediaMetadataRepository;
import com.historytalk.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class DocumentMediaServicePerformanceTest {

    @Autowired
    private DocumentMediaService documentMediaService;

    @MockBean
    private DocumentRepository documentRepository;

    @MockBean
    private DocumentMediaMetadataRepository mediaMetadataRepository;

    @MockBean
    private SupabaseDocumentStorageService supabaseDocumentStorageService;

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
    void testPresignedUrlGenerationLatency() {
        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(supabaseDocumentStorageService.generatePresignedUploadUrl(anyString(), anyString(), anyLong()))
                .thenReturn("https://supabase.com/storage/presigned-url");

        long startTime = System.nanoTime();
        PresignedUploadUrlResponse response = documentMediaService.generatePresignedUploadUrl(
                docId.toString(), uploadRequest, "user123", "CONTENT_ADMIN");
        long endTime = System.nanoTime();

        long durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

        assertNotNull(response);
        assertTrue(durationMs < 100, "Presigned URL generation should be < 100ms, took: " + durationMs + "ms");
    }

    @Test
    void testUploadConfirmationLatency() {
        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenReturn(document);
        when(mediaMetadataRepository.save(any(DocumentMediaMetadata.class))).thenAnswer(invocation -> {
            DocumentMediaMetadata metadata = invocation.getArgument(0);
            metadata.setMetadataId(UUID.randomUUID());
            return metadata;
        });

        long startTime = System.nanoTime();
        DocumentMediaMetadataResponse response = documentMediaService.confirmUpload(
                docId.toString(), confirmationRequest, "user123", "CONTENT_ADMIN");
        long endTime = System.nanoTime();

        long durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

        assertNotNull(response);
        assertTrue(durationMs < 200, "Upload confirmation should be < 200ms, took: " + durationMs + "ms");
    }

    @Test
    void testSignedViewUrlGenerationLatency() {
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

        long startTime = System.nanoTime();
        SignedViewUrlResponse response = documentMediaService.generateSignedViewUrl(
                docId.toString(), 300, 300, "user123", "USER");
        long endTime = System.nanoTime();

        long durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

        assertNotNull(response);
        assertTrue(durationMs < 100, "Signed view URL generation should be < 100ms, took: " + durationMs + "ms");
    }

    @Test
    void testConcurrentPresignedUrlGeneration() throws InterruptedException {
        when(documentRepository.findById(any(UUID.class))).thenReturn(Optional.of(document));
        when(supabaseDocumentStorageService.generatePresignedUploadUrl(anyString(), anyString(), anyLong()))
                .thenReturn("https://supabase.com/storage/presigned-url");

        int threadCount = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        long startTime = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    PresignedUploadUrlResponse response = documentMediaService.generatePresignedUploadUrl(
                            docId.toString(), uploadRequest, "user123", "CONTENT_ADMIN");
                    if (response != null && response.getUploadUrl() != null) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        long endTime = System.nanoTime();
        long durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

        assertEquals(threadCount, successCount.get(), "All requests should succeed");
        assertEquals(0, failureCount.get(), "No requests should fail");
        assertTrue(durationMs < 5000, "100 concurrent requests should complete in < 5s, took: " + durationMs + "ms");
    }

    @Test
    void testConcurrentUploadConfirmation() throws InterruptedException {
        when(documentRepository.findById(any(UUID.class))).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenReturn(document);
        when(mediaMetadataRepository.save(any(DocumentMediaMetadata.class))).thenAnswer(invocation -> {
            DocumentMediaMetadata metadata = invocation.getArgument(0);
            metadata.setMetadataId(UUID.randomUUID());
            return metadata;
        });

        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        long startTime = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    DocumentMediaMetadataResponse response = documentMediaService.confirmUpload(
                            docId.toString(), confirmationRequest, "user123", "CONTENT_ADMIN");
                    if (response != null && response.getMetadataId() != null) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        long endTime = System.nanoTime();
        long durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

        assertEquals(threadCount, successCount.get(), "All requests should succeed");
        assertEquals(0, failureCount.get(), "No requests should fail");
        assertTrue(durationMs < 10000, "50 concurrent requests should complete in < 10s, took: " + durationMs + "ms");
    }

    @Test
    void testDatabaseQueryPerformance() {
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

        int iterations = 1000;
        long startTime = System.nanoTime();

        for (int i = 0; i < iterations; i++) {
            SignedViewUrlResponse response = documentMediaService.generateSignedViewUrl(
                    docId.toString(), 300, 300, "user123", "USER");
            assertNotNull(response);
        }

        long endTime = System.nanoTime();
        long durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
        double avgLatencyMs = (double) durationMs / iterations;

        assertTrue(avgLatencyMs < 10, "Average query latency should be < 10ms, was: " + avgLatencyMs + "ms");
    }

    @Test
    void testMemoryPressureUnderLoad() throws InterruptedException {
        when(documentRepository.findById(any(UUID.class))).thenReturn(Optional.of(document));
        when(supabaseDocumentStorageService.generatePresignedUploadUrl(anyString(), anyString(), anyLong()))
                .thenReturn("https://supabase.com/storage/presigned-url");

        int threadCount = 200;
        int iterationsPerThread = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        long startTime = System.nanoTime();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        PresignedUploadUrlResponse response = documentMediaService.generatePresignedUploadUrl(
                                docId.toString(), uploadRequest, "user123", "CONTENT_ADMIN");
                        if (response != null) {
                            successCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    // Log error but continue
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executorService.shutdown();

        long endTime = System.nanoTime();
        long durationMs = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);

        int expectedSuccess = threadCount * iterationsPerThread;
        assertTrue(successCount.get() >= expectedSuccess * 0.95, 
                "At least 95% of requests should succeed under load");
        assertTrue(durationMs < 30000, "Load test should complete in < 30s, took: " + durationMs + "ms");
    }
}
