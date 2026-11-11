package com.historytalk.service.document;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Periodically purges temporary/staging files in Supabase Storage.
 * Direct deletion on Supabase storage tables is locked by 'storage.protect_delete()',
 * so all deletions must go through the Supabase Storage REST API.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "supabase.storage.staging.enabled", havingValue = "true", matchIfMissing = true)
public class StagingCleanupScheduler {

    private final SupabaseDocumentStorageService storageService;

    @Value("${supabase.storage.staging.ttl-minutes:60}")
    private long ttlMinutes;

    /**
     * Cleans up staging historical images older than the TTL threshold.
     * Default runs every 15 minutes (900,000 ms), with an initial delay of 1 minute after startup.
     */
    @Scheduled(fixedDelayString = "${supabase.storage.staging.cleanup-interval-ms:900000}", initialDelay = 60000)
    public void cleanupExpiredStagingImages() {
        log.info("Starting scheduled staging cleanup (TTL: {} minutes)...", ttlMinutes);
        int purged = storageService.purgeExpiredStagingObjects(
                "staging/historical-images",
                Duration.ofMinutes(ttlMinutes)
        );
        log.info("Scheduled staging cleanup completed. Total objects purged: {}", purged);
    }
}
