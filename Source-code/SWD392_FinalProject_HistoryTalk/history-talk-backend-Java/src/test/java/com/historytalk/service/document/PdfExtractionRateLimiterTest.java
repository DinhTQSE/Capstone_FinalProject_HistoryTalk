package com.historytalk.service.document;

import com.historytalk.exception.RateLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfExtractionRateLimiterTest {

    private PdfExtractionRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new PdfExtractionRateLimiter();
    }

    @Test
    void acquire_shouldAllowFirstRequest() {
        assertThatCode(() -> rateLimiter.acquire("user-1", false))
                .doesNotThrowAnyException();
    }

    @Test
    void acquire_shouldBlockConcurrentRequestsFromSameUser() {
        rateLimiter.acquire("user-1", false);

        assertThatThrownBy(() -> rateLimiter.acquire("user-1", false))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Bạn đang có một tài liệu đang được trích xuất");
    }

    @Test
    void acquire_shouldAllowDifferentUsersConcurrently() {
        rateLimiter.acquire("user-1", false);

        assertThatCode(() -> rateLimiter.acquire("user-2", false))
                .doesNotThrowAnyException();
    }

    @Test
    void acquire_shouldEnforceBurstCooldownAfterRelease() {
        String token = rateLimiter.acquire("user-1", false);
        rateLimiter.release("user-1", token);

        // Immediately requesting again should trigger 3s cooldown
        assertThatThrownBy(() -> rateLimiter.acquire("user-1", false))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Thao tác quá nhanh");
    }

    @Test
    void release_shouldIgnoreMismatchedToken() {
        String token1 = rateLimiter.acquire("user-1", false);
        
        // Attempting to release with a wrong/stale token should NOT release the lock
        rateLimiter.release("user-1", "stale-wrong-token");

        // The user should still be locked
        assertThatThrownBy(() -> rateLimiter.acquire("user-1", false))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("Bạn đang có một tài liệu đang được trích xuất");
    }

    @Test
    void release_shouldAllowNextRequestAfterCooldown() throws InterruptedException {
        String token = rateLimiter.acquire("user-1", false);
        rateLimiter.release("user-1", token);

        // Wait past 3s cooldown
        Thread.sleep(3050L);

        assertThatCode(() -> rateLimiter.acquire("user-1", false))
                .doesNotThrowAnyException();
    }
}
