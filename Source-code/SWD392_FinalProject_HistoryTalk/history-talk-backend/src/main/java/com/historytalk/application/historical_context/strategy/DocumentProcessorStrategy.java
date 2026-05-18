package com.historytalk.application.historical_context.strategy;

import com.historytalk.dataaccess.shared.entity.enums.DocumentType;

public interface DocumentProcessorStrategy {
    
    DocumentType getSupportedType();
    
    /**
     * Parse, validate, and sanitize content based on the document type.
     * 
     * @param content Raw content from user
     * @return Processed content ready to be saved
     * @throws IllegalArgumentException if content is invalid
     */
    String processContent(String content);
}
