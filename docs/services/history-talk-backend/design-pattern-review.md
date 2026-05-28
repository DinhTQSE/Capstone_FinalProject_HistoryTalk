# Design Pattern Review

Last verified: 2026-05-28

## Patterns In Use

- Strategy pattern: `DocumentProcessorStrategy` defines the document-processing contract. Current implementations include `TextProcessorStrategy` and `MarkdownProcessorStrategy`.
- Factory pattern: `DocumentProcessorFactory` selects a `DocumentProcessorStrategy` by `DocumentType` and rejects unsupported types.
- Service orchestration: `HistoricalContextDocumentServiceImpl` delegates document content validation/sanitization to the selected strategy before persistence.
- Builder pattern: Lombok `@Builder` is used across DTO/entity construction where the source code already follows that style.

## Current Source Paths

```text
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/historicalContext/strategy/DocumentProcessorStrategy.java
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/historicalContext/strategy/TextProcessorStrategy.java
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/historicalContext/strategy/MarkdownProcessorStrategy.java
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/historicalContext/strategy/DocumentProcessorFactory.java
Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java/src/main/java/com/historytalk/service/historicalContext/HistoricalContextDocumentServiceImpl.java
```

## Notes

- The previous inactive backend directory and old mixed-case Java package path are obsolete.
- Keep document-format behavior behind strategies. Add a new strategy implementation when introducing a new document type instead of expanding conditional logic inside the service.
- Focused unit tests for each strategy would improve coverage for null/blank content, size limits, and sanitization behavior.
