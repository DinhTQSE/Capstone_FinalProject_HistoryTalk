package com.historytalk.service.chat;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiMetricsService {

    private static final String DEFAULT_PROVIDER = "unknown";
    private static final String DEFAULT_MODEL = "unknown";

    private final MeterRegistry meterRegistry;

    public void recordRequest(String operation, String status) {
        Counter.builder("historytalk.ai.requests")
                .description("Total AI service requests made by the Java backend")
                .tag("operation", normalize(operation))
                .tag("status", normalize(status))
                .register(meterRegistry)
                .increment();
    }

    public void recordTokens(TokenUsage usage) {
        if (usage == null) {
            return;
        }
        incrementTokens(usage.provider(), usage.model(), "prompt", usage.promptTokens());
        incrementTokens(usage.provider(), usage.model(), "completion", usage.completionTokens());
        incrementTokens(usage.provider(), usage.model(), "total", usage.totalTokens());
    }

    private void incrementTokens(String provider, String model, String type, Integer amount) {
        if (amount == null || amount <= 0) {
            return;
        }
        Counter.builder("historytalk.ai.tokens")
                .description("Total LLM tokens reported by the AI service")
                .tag("provider", normalizeOrDefault(provider, DEFAULT_PROVIDER))
                .tag("model", normalizeOrDefault(model, DEFAULT_MODEL))
                .tag("type", normalize(type))
                .register(meterRegistry)
                .increment(amount);
    }

    private String normalize(String value) {
        return normalizeOrDefault(value, "unknown");
    }

    private String normalizeOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value.trim().toLowerCase();
    }

    public record TokenUsage(
            String provider,
            String model,
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens) {
    }
}
