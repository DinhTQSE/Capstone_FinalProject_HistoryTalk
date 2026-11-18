package com.historytalk.service.document;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StagingCleanupSchedulerTest {

    @Mock
    private SupabaseDocumentStorageService storageService;

    @InjectMocks
    private StagingCleanupScheduler stagingCleanupScheduler;

    @Test
    @DisplayName("WF-02 / F-06: Scheduled staging cleanup purges expired historical images with configured TTL")
    void cleanupExpiredStagingImages_PurgesWithConfiguredTtl() {
        ReflectionTestUtils.setField(stagingCleanupScheduler, "ttlMinutes", 60L);
        when(storageService.purgeExpiredStagingObjects("staging/historical-images", Duration.ofMinutes(60)))
                .thenReturn(4);

        stagingCleanupScheduler.cleanupExpiredStagingImages();

        verify(storageService).purgeExpiredStagingObjects("staging/historical-images", Duration.ofMinutes(60));
    }
}
