package com.historyTalk.service.historicalContext.strategy;

import com.historyTalk.entity.enums.DocumentType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class DocumentProcessorFactory {

    private final Map<DocumentType, DocumentProcessorStrategy> strategies;

    @Autowired
    public DocumentProcessorFactory(List<DocumentProcessorStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(DocumentProcessorStrategy::getSupportedType, s -> s));
    }

    public DocumentProcessorStrategy getStrategy(DocumentType type) {
        DocumentProcessorStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new UnsupportedOperationException("Unsupported document type: " + type);
        }
        return strategy;
    }
}
