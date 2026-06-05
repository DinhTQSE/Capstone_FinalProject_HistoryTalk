package com.historytalk.service.historicalContext.strategy;

import com.historytalk.entity.enums.DocumentType;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class TextProcessorStrategy implements DocumentProcessorStrategy {

    private static final long MAX_CONTENT_BYTES = 10 * 1024 * 1024; // 10MB

    @Override
    public DocumentType getSupportedType() {
        return DocumentType.TEXT;
    }

    @Override
    public String processContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Nội dung văn bản không được để trống");
        }
        long contentSize = content.getBytes(StandardCharsets.UTF_8).length;
        if (contentSize > MAX_CONTENT_BYTES) {
            throw new IllegalArgumentException("Kích thước nội dung văn bản vượt quá giới hạn 10MB");
        }
        return content.trim();
    }
}
