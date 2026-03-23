package com.historyTalk.service.historicalContext.strategy;

import com.historyTalk.entity.enums.DocumentType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class MarkdownProcessorStrategy implements DocumentProcessorStrategy {

    private static final long MAX_CONTENT_BYTES = 10 * 1024 * 1024; // 10MB

    @Override
    public DocumentType getSupportedType() {
        return DocumentType.MARKDOWN;
    }

    @Override
    public String processContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Markdown content cannot be blank");
        }
        long contentSize = content.getBytes(StandardCharsets.UTF_8).length;
        if (contentSize > MAX_CONTENT_BYTES) {
            throw new IllegalArgumentException("Markdown content size exceeds 10MB limit");
        }
        
        // Basic sanitization: remove script tags to prevent XSS
        String sanitized = content.replaceAll("(?i)<script.*?>.*?</script.*?>", "");
        return sanitized.trim();
    }
}
