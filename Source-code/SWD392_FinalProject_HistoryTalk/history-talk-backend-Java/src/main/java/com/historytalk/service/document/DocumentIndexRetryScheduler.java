package com.historytalk.service.document;

import com.historytalk.entity.document.Document;
import com.historytalk.repository.DocumentRepository;
import com.historytalk.service.chat.AiServiceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Re-sends documents that have text but no vector chunk to the AI service. Indexing is fire-and-forget on save,
 * so a document saved while the AI/GPU server was down would otherwise never be searchable by RAG.
 * The AI service deletes a document's old chunks before indexing it, so re-sending is safe.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ai.document-index-retry.enabled", havingValue = "true", matchIfMissing = true)
public class DocumentIndexRetryScheduler {

    private final DocumentRepository documentRepository;
    private final AiServiceClient aiServiceClient;

    @Value("${ai.document-index-retry.batch-size:10}")
    private int batchSize;

    @Value("${ai.document-index-retry.settle-minutes:5}")
    private long settleMinutes;

    /** Default runs every 10 minutes, starting 2 minutes after startup. */
    @Scheduled(fixedDelayString = "${ai.document-index-retry.interval-ms:600000}", initialDelay = 120000)
    public void reindexMissingDocuments() {
        List<Document> pending = documentRepository.findUnindexedDocuments(
                LocalDateTime.now().minusMinutes(settleMinutes), batchSize);
        if (pending.isEmpty()) {
            return;
        }
        log.info("Re-indexing {} document(s) without vector chunks...", pending.size());
        int indexed = 0;
        int consecutiveFailures = 0;
        for (Document doc : pending) {
            if (aiServiceClient.processDocument(doc.getDocId().toString(), doc.getEntityId().toString(), doc.getContent())) {
                indexed++;
                consecutiveFailures = 0;
            } else if (++consecutiveFailures >= 3) {
                // AI service is most likely down: try the rest on the next run.
                break;
            }
        }
        log.info("Document re-index run completed: {}/{} indexed", indexed, pending.size());
    }
}
