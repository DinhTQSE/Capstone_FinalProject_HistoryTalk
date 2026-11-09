package com.historytalk.service.document;

import com.historytalk.exception.RateLimitExceededException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe rate limiter and concurrency guard for PDF text extraction.
 * Protects server resources (CPU, RAM, VLM tokens) against spam and double-clicks,
 * while allowing normal users to re-upload files without friction.
 */
@Component
@Slf4j
public class PdfExtractionRateLimiter {

    // Khoảng cách tối thiểu giữa 2 lần bấm upload (chống double-click và script bắn dồn dập)
    private static final long BURST_COOLDOWN_MS = 3_000L; // 3 giây

    // Cửa sổ trượt tính hạn mức
    private static final long WINDOW_MS = 5 * 60 * 1000L; // 5 phút

    // Hạn mức tối đa trong 5 phút
    private static final int MAX_REGULAR_PER_WINDOW = 10;
    private static final int MAX_VISION_PER_WINDOW = 3;

    // Thời gian không hoạt động trước khi xóa khỏi RAM
    private static final long IDLE_EVICTION_MS = 15 * 60 * 1000L; // 15 phút

    private final Map<String, UserExtractionState> userStates = new ConcurrentHashMap<>();

    @Getter
    @Setter
    public static class UserExtractionState {
        private volatile boolean isProcessing = false;
        private volatile long lastRequestTimeMs = 0L;
        private final Deque<Long> regularTimestamps = new ArrayDeque<>();
        private final Deque<Long> visionTimestamps = new ArrayDeque<>();
    }

    /**
     * Atomically check and acquire extraction permission for a user.
     *
     * @param userId ID of the requesting user
     * @param enableAiVision whether AI Vision extraction is requested
     * @throws RateLimitExceededException if user violates concurrency, cooldown, or quota limits
     */
    public void acquire(String userId, boolean enableAiVision) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        long now = System.currentTimeMillis();

        userStates.compute(userId, (key, existingState) -> {
            UserExtractionState state = (existingState != null) ? existingState : new UserExtractionState();

            // 1. TẦNG 1: Active Task Lock (1 user chỉ có tối đa 1 tiến trình trích xuất đang chạy)
            if (state.isProcessing()) {
                throw new RateLimitExceededException(
                        "Bạn đang có một tài liệu đang được trích xuất. Vui lòng chờ hoàn tất trước khi tải lên tài liệu mới."
                );
            }

            // 2. TẦNG 2: Burst Cooldown (Tối thiểu 3 giây giữa 2 lần gửi)
            if (state.getLastRequestTimeMs() > 0 && (now - state.getLastRequestTimeMs()) < BURST_COOLDOWN_MS) {
                long remainingSec = Math.max(1, (BURST_COOLDOWN_MS - (now - state.getLastRequestTimeMs())) / 1000 + 1);
                throw new RateLimitExceededException(
                        "Thao tác quá nhanh. Vui lòng thử lại sau " + remainingSec + " giây."
                );
            }

            // 3. TẦNG 3: Sliding Window Rate Limit (Dọn mốc thời gian ngoài 5 phút)
            long cutoff = now - WINDOW_MS;
            cleanOldTimestamps(state.getRegularTimestamps(), cutoff);
            cleanOldTimestamps(state.getVisionTimestamps(), cutoff);

            if (enableAiVision) {
                if (state.getVisionTimestamps().size() >= MAX_VISION_PER_WINDOW) {
                    throw new RateLimitExceededException(
                            "Bạn đã đạt giới hạn trích xuất AI (" + MAX_VISION_PER_WINDOW + " lần trong 5 phút). Vui lòng thử lại sau ít phút."
                    );
                }
                state.getVisionTimestamps().addLast(now);
            } else {
                if (state.getRegularTimestamps().size() >= MAX_REGULAR_PER_WINDOW) {
                    throw new RateLimitExceededException(
                            "Bạn đã tải lên quá nhiều tài liệu (" + MAX_REGULAR_PER_WINDOW + " lần trong 5 phút). Vui lòng thử lại sau ít phút."
                    );
                }
                state.getRegularTimestamps().addLast(now);
            }

            state.setLastRequestTimeMs(now);
            state.setProcessing(true);
            return state;
        });

        log.debug("Acquired extraction lock for user: {}, enableAiVision: {}", userId, enableAiVision);
    }

    /**
     * Release active task lock for a user once extraction completes, fails, or disconnects.
     *
     * @param userId ID of the requesting user
     */
    public void release(String userId) {
        if (userId == null || userId.isBlank()) {
            return;
        }

        UserExtractionState state = userStates.get(userId);
        if (state != null) {
            state.setProcessing(false);
            log.debug("Released extraction lock for user: {}", userId);
        }
    }

    private void cleanOldTimestamps(Deque<Long> timestamps, long cutoff) {
        while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
            timestamps.pollFirst();
        }
    }

    /**
     * Scheduled memory eviction: Quét định kỳ mỗi 10 phút,
     * xóa toàn bộ các user không còn hoạt động trong 15 phút qua để giải phóng RAM triệt để.
     */
    @Scheduled(fixedDelay = 600_000L) // 10 minutes
    public void evictIdleUserStates() {
        long cutoff = System.currentTimeMillis() - IDLE_EVICTION_MS;
        int beforeSize = userStates.size();

        userStates.entrySet().removeIf(entry -> {
            UserExtractionState state = entry.getValue();
            return !state.isProcessing() && state.getLastRequestTimeMs() < cutoff;
        });

        int evictedCount = beforeSize - userStates.size();
        if (evictedCount > 0) {
            log.info("Evicted {} idle users from PdfExtractionRateLimiter cache (remaining: {})",
                    evictedCount, userStates.size());
        }
    }
}
