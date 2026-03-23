# Document Processor Strategy Plan

**Date:** March 23, 2026
**Target Service:** `HistoricalContextDocumentService` (Chosen as the service with the lowest impact on core system stability, handling only supplementary documents).

## 1. Problem Introduction

Currently, `HistoricalContextDocumentServiceImpl` validates all `content` using a single hardcoded logic block: it only checks if the content is blank and restricts the size to 10MB via `validateContent()`.

However, the uploaded `content` can be in different formats in the future:
- **Plain Text**: requires simple trimming and size validation.
- **Markdown**: requires sanitizing HTML tags to prevent XSS attacks while preserving formatting.

If the current monolithic structure is maintained, methods like `createDocument()` will soon be littered with huge `if-else` or `switch-case` blocks to handle these distinct formats, violating the Open/Closed Principle making the code much harder to maintain.

**Solution:** Apply the **Strategy Pattern** combined with the **Factory Pattern** to cleanly manage the parsing and validation of documents based on a `DocumentType`.

---

## 2. Architecture Design (Workflow)

### Strategy + Factory Workflow:
1. The Client sends a `CreateHistoricalContextDocumentRequest` or `UpdateHistoricalContextDocumentRequest` containing the raw content and a `DocumentType` (TEXT or MARKDOWN).
2. `HistoricalContextDocumentServiceImpl` queries the `DocumentProcessorFactory` using the provided type: `processorFactory.getStrategy(type)`.
3. The Factory returns the matching **Concrete Strategy** (e.g., `MarkdownProcessorStrategy`).
4. The service invokes the `processContent(content)` method on the returned Strategy.
5. Each Concrete Strategy performs its own unique sanitization, parsing, and validation logic before returning the processed content to be saved to the database.

---

## 3. Implementation Details

### Step 1: Add `DocumentType` Enum
Define the supported document formats:
```java
public enum DocumentType {
    TEXT,
    MARKDOWN
}
```

### Step 2: Create Strategy Interface
All processors must conform to this contract:
```java
public interface DocumentProcessorStrategy {
    DocumentType getSupportedType();
    
    /**
     * @param content Raw content from user
     * @return Sanitized content to save into DB
     * @throws IllegalArgumentException if format is invalid
     */
    String processContent(String content);
}
```

### Step 3: Create Concrete Strategy Classes
Specific implementations for each document format:

**TextProcessorStrategy.java**
```java
@Component
public class TextProcessorStrategy implements DocumentProcessorStrategy {
    @Override
    public DocumentType getSupportedType() { return DocumentType.TEXT; }

    @Override
    public String processContent(String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Text cannot be blank");
        if (content.getBytes(StandardCharsets.UTF_8).length > 10 * 1024 * 1024) throw new IllegalArgumentException("Exceeds 10MB");
        return content.trim();
    }
}
```

**MarkdownProcessorStrategy.java**
```java
@Component
public class MarkdownProcessorStrategy implements DocumentProcessorStrategy {
    @Override
    public DocumentType getSupportedType() { return DocumentType.MARKDOWN; }

    @Override
    public String processContent(String content) {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("Markdown cannot be blank");
        if (content.getBytes(StandardCharsets.UTF_8).length > 10 * 1024 * 1024) throw new IllegalArgumentException("Exceeds 10MB");
        
        // Basic sanitization: optionally strip <script> tags for security
        String sanitized = content.replaceAll("(?i)<script.*?>.*?</script.*?>", "");
        return sanitized.trim();
    }
}
```

### Step 4: Create the Factory Class
Utilize Spring Boot's dependency injection to map all strategies:
```java
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
```

### Step 5: Update Entity, Service, and DTOs
- Add `DocumentType documentType` field to `HistoricalContextDocument.java`, response, and request DTOs.
- Update `HistoricalContextDocumentServiceImpl` to use the new Factory.

## 4. Workflow Benefits
1. **Single Responsibility Principle:** HTML sanitization logic for Markdown is completely isolated from the standard plain-text validations.
2. **Open/Closed Principle:** If a new document type is required in the future (e.g., `PDF`), developers only need to create a new `PdfProcessorStrategy` class. The Factory will automatically auto-wire the new strategy without a single line of code modified in the core Service layer.
3. **Safe Testing Environment:** Developers can easily write isolated unit tests for `MarkdownProcessorStrategy` logic without mocking the large service database injections.
