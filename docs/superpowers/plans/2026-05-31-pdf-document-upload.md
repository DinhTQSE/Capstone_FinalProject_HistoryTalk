# PDF File Upload And Download For Existing Document Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Java backend endpoints to upload and download a PDF file through Supabase Storage for an existing `document` row by `doc_id`.

**Architecture:** The client sends `docId` and a PDF file to a single document-level upload endpoint. Java validates role and file, loads the unique `document` row by `doc_id`, uploads the PDF binary to Supabase Storage, updates only `document.file_url` and `document.document_type = PDF`, then returns a document response. A matching download endpoint loads `document.file_url`, downloads the PDF from Supabase Storage server-side, and streams it back to the caller for testing/manual verification. No AI backend call, no PDF text extraction, no `document.content` mutation, and no new table.

**Tech Stack:** Spring Boot 3.2.5, Java 21, Spring MVC multipart, Supabase Storage REST API via `RestClient`, JUnit 5, Mockito.

---

## Scope Decision

This plan is Java backend only.

Do not:

- Call AI backend.
- Add AI endpoints.
- Extract PDF text.
- Store PDF text in `document.content`.
- Create a new table.
- Create a new `document` row during PDF upload.
- Add separate context/character upload endpoints.
- Expose the Supabase service-role key to clients.

Do:

- Add one generic upload endpoint: `POST /api/v1/documents/{docId}/upload-pdf`.
- Add one generic download endpoint: `GET /api/v1/documents/{docId}/download-pdf`.
- Upload PDF binary to Supabase Storage.
- Download PDF binary from Supabase Storage through Java.
- Update the existing row in the existing `document` table.
- Store the Supabase object path in `document.file_url`.
- Set `document.document_type = PDF`.

## Identifier Decision

Use `docId`.

Reason:

- `document.doc_id` uniquely identifies the row to update/download.
- `document.entity_id` identifies the owning context/character and is not unique across documents.
- A single endpoint is enough because `doc_id` is unique. After loading `Document`, backend can derive `entity_type` and `entity_id` for storage path generation.

---

## API Contract

Upload:

```text
POST /api/v1/documents/{docId}/upload-pdf
Content-Type: multipart/form-data
Role: CONTENT_ADMIN or SYSTEM_ADMIN
Parts:
- file: PDF file
```

Download:

```text
GET /api/v1/documents/{docId}/download-pdf
Role: CONTENT_ADMIN or SYSTEM_ADMIN
```

Upload success response:

```json
{
  "success": true,
  "message": "PDF file uploaded successfully",
  "data": {
    "docId": "...",
    "entityId": "...",
    "entityType": "CONTEXT",
    "title": "...",
    "fileUrl": "documents/context/<entityId>/<docId>.pdf",
    "type": "PDF",
    "uploadDate": "...",
    "updatedDate": "..."
  },
  "timestamp": "..."
}
```

Download success response:

- `200 OK`
- `Content-Type: application/pdf`
- `Content-Disposition: attachment; filename="<docId>.pdf"`
- Body: PDF bytes downloaded from Supabase Storage.

Status codes:

- `200 OK`: upload updated existing document, or download streamed PDF.
- `400 BAD_REQUEST`: invalid UUID, empty file, non-PDF filename, invalid content type, file too large, document has no PDF.
- `401 UNAUTHORIZED`: no/invalid token.
- `403 FORBIDDEN`: user lacks `CONTENT_ADMIN` or `SYSTEM_ADMIN`.
- `404 NOT_FOUND`: document not found.
- `500 INTERNAL_SERVER_ERROR`: Supabase upload/download failure.

---

## Files To Modify Or Create

Java backend root:

`Source-code/SWD392_FinalProject_HistoryTalk/history-talk-backend-Java`

Modify:

- `src/main/java/com/historytalk/entity/enums/DocumentType.java`
- `src/main/resources/application.properties`

Create:

- `src/main/java/com/historytalk/dto/document/DocumentFileResponse.java`
- `src/main/java/com/historytalk/service/document/UploadedDocumentFile.java`
- `src/main/java/com/historytalk/service/document/DownloadedDocumentFile.java`
- `src/main/java/com/historytalk/service/document/SupabaseDocumentStorageService.java`
- `src/main/java/com/historytalk/service/document/DocumentFileService.java`
- `src/main/java/com/historytalk/service/document/DocumentFileServiceImpl.java`
- `src/main/java/com/historytalk/controller/document/DocumentFileController.java`
- `src/test/java/com/historytalk/service/document/SupabaseDocumentStorageServiceTest.java`
- `src/test/java/com/historytalk/service/document/DocumentFileServiceImplTest.java`

---

### Task 1: Add PDF Document Type

**Files:**
- Modify: `src/main/java/com/historytalk/entity/enums/DocumentType.java`

- [ ] Add enum value:

```java
package com.historytalk.entity.enums;

public enum DocumentType {
    TEXT,
    MARKDOWN,
    PDF
}
```

---

### Task 2: Add Supabase PDF Storage Service

**Files:**
- Create: `src/main/java/com/historytalk/service/document/UploadedDocumentFile.java`
- Create: `src/main/java/com/historytalk/service/document/DownloadedDocumentFile.java`
- Create: `src/main/java/com/historytalk/service/document/SupabaseDocumentStorageService.java`
- Modify: `src/main/resources/application.properties`
- Test: `src/test/java/com/historytalk/service/document/SupabaseDocumentStorageServiceTest.java`

- [ ] Create upload result:

```java
package com.historytalk.service.document;

public record UploadedDocumentFile(String objectPath) {
}
```

- [ ] Create download result:

```java
package com.historytalk.service.document;

public record DownloadedDocumentFile(byte[] bytes, String contentType) {
}
```

- [ ] Storage service responsibilities:

```java
public UploadedDocumentFile uploadPdf(EntityType entityType, UUID entityId, UUID docId, MultipartFile file)
```

Rules:

- Validate non-empty file.
- Validate max size `10MB`.
- Validate filename ends with `.pdf`.
- Validate content type is `application/pdf` when present.
- Upload to Supabase object path:

```text
documents/<entityType-lowercase>/<entityId>/<docId>.pdf
```

- Include header `x-upsert: true` so re-uploading the same document overwrites the prior PDF.

```java
public DownloadedDocumentFile downloadPdf(String objectPath)
```

Rules:

- Download from:

```text
/storage/v1/object/<bucket>/<objectPath>
```

- Return bytes and `application/pdf`.
- Throw `SystemException` if Supabase fails or returns empty bytes.

- [ ] Add config:

```properties
# Document upload/download
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=12MB
supabase.url=${SUPABASE_URL}
supabase.service-role-key=${SUPABASE_SERVICE_ROLE_KEY}
supabase.storage.bucket=${SUPABASE_STORAGE_BUCKET:documents}
```

- [ ] Storage tests:

Test:

- valid PDF upload returns `documents/context/<entityId>/<docId>.pdf`.
- upload request includes auth headers and `x-upsert: true`.
- non-PDF extension throws `InvalidRequestException`.
- empty file throws `InvalidRequestException`.
- download returns PDF bytes.
- Supabase upload/download failure throws `SystemException`.

---

### Task 3: Add Generic Document File Response

**Files:**
- Create: `src/main/java/com/historytalk/dto/document/DocumentFileResponse.java`

```java
package com.historytalk.dto.document;

import com.historytalk.entity.enums.DocumentType;
import com.historytalk.entity.enums.EntityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentFileResponse {
    private String docId;
    private String entityId;
    private EntityType entityType;
    private String title;
    private String fileUrl;
    private DocumentType type;
    private LocalDateTime uploadDate;
    private LocalDateTime updatedDate;
}
```

Reason: existing response DTOs are split by context/character. A generic endpoint should return a generic document file response and avoid leaking full `content`.

---

### Task 4: Add Generic Document File Service

**Files:**
- Create: `src/main/java/com/historytalk/service/document/DocumentFileService.java`
- Create: `src/main/java/com/historytalk/service/document/DocumentFileServiceImpl.java`
- Test: `src/test/java/com/historytalk/service/document/DocumentFileServiceImplTest.java`

Interface:

```java
package com.historytalk.service.document;

import com.historytalk.dto.document.DocumentFileResponse;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentFileService {
    DocumentFileResponse uploadPdfFile(String docId, MultipartFile file, String userId, String userRole);

    DownloadedDocumentFile downloadPdfFile(String docId, String userId, String userRole);
}
```

Upload service rules:

- Reject non-staff/admin role with `InvalidRequestException`.
- Load `Document` by `docId`, else `ResourceNotFoundException`.
- Call `supabaseDocumentStorageService.uploadPdf(doc.entityType, doc.entityId, doc.docId, file)`.
- Set `fileUrl` from returned object path.
- Set `documentType = PDF`.
- Do not update `content`.
- Save and return `DocumentFileResponse`.

Download service rules:

- Reject non-staff/admin role with `InvalidRequestException`.
- Load `Document` by `docId`, else `ResourceNotFoundException`.
- Reject if `documentType != PDF`.
- Reject if `fileUrl` is null/blank.
- Call `supabaseDocumentStorageService.downloadPdf(doc.fileUrl)`.
- Return `DownloadedDocumentFile`.

Service tests:

- upload updates `fileUrl`.
- upload changes `documentType` to `PDF`.
- upload keeps existing `content` unchanged.
- upload passes `entityType`, `entityId`, and `docId` from loaded document into storage.
- upload rejects non-staff role before loading document.
- upload missing document throws `ResourceNotFoundException`.
- download rejects document without uploaded PDF.
- download calls storage with `document.fileUrl`.

---

### Task 5: Add Generic Document File Controller

**Files:**
- Create: `src/main/java/com/historytalk/controller/document/DocumentFileController.java`

Controller endpoints:

```java
POST /api/v1/documents/{docId}/upload-pdf
GET  /api/v1/documents/{docId}/download-pdf
```

Upload response:

```java
ResponseEntity<ApiResponse<DocumentFileResponse>>
```

Download response:

```java
ResponseEntity<byte[]>
```

Download headers:

```java
.contentType(MediaType.APPLICATION_PDF)
.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + docId + ".pdf\"")
```

Both endpoints:

- `@PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")`
- `@SecurityRequirement(name = "bearerAuth")`

---

### Task 6: Final Verification

- [ ] Run focused tests:

```powershell
mvn -q "-Dtest=SupabaseDocumentStorageServiceTest,DocumentFileServiceImplTest" test
```

- [ ] Run compile:

```powershell
mvn -q -DskipTests compile
```

- [ ] Run all tests:

```powershell
mvn -q test
```

- [ ] Manual upload check:

```powershell
curl.exe -X POST "http://localhost:8080/Historical-tell/api/v1/documents/<DOC_UUID>/upload-pdf" `
  -H "Authorization: Bearer <CONTENT_ADMIN_TOKEN>" `
  -F "file=@C:\path\to\source.pdf;type=application/pdf"
```

- [ ] Manual download check:

```powershell
curl.exe -L -X GET "http://localhost:8080/Historical-tell/api/v1/documents/<DOC_UUID>/download-pdf" `
  -H "Authorization: Bearer <CONTENT_ADMIN_TOKEN>" `
  -o downloaded-source.pdf
```

Expected:

- Upload response status `200 OK`.
- `data.docId` equals `<DOC_UUID>`.
- `data.fileUrl` is a Supabase object path.
- `data.type` is `PDF`.
- Upload response does not include full `content`.
- Existing `document.content` in DB is unchanged.
- Download response is a valid PDF file.
- No AI backend calls occur.

---

## Database Decision

Use existing table `document`.

Upload updates only:

- `file_url`
- `document_type`
- `updated_at` via Hibernate update timestamp

Do not update:

- `content`
- `entity_id`
- `entity_type`
- `uploaded_by`
- `title`

No migration is required unless later the product wants extra file metadata such as `original_file_name`, `mime_type`, `file_size`, or `storage_bucket`.
