package com.historyTalk.controller.historicalContext;

import com.historyTalk.dto.ApiResponse;
import com.historyTalk.dto.historicalContext.CreateHistoricalContextDocumentRequest;
import com.historyTalk.dto.historicalContext.HistoricalContextDocumentResponse;
import com.historyTalk.dto.historicalContext.UpdateHistoricalContextDocumentRequest;
import com.historyTalk.service.historicalContext.HistoricalContextDocumentService;
import com.historyTalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Historical Context Document Management
 * 
 * Endpoints:
 * - GET /v1/historical-documents - Get all documents (public)
 * - GET /v1/historical-documents/{docId} - Get single document (public)
 * - GET /v1/historical-documents/search - Search documents (public)
 * - POST /v1/historical-documents - Upload/create document (Staff/Admin)
 * - PUT /v1/historical-documents/{docId} - Update document (Staff/Admin)
 * - DELETE /v1/historical-documents/{docId} - Remove document (Staff/Admin)
 */
@RestController
@RequestMapping("/api/v1/historical-documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Historical Context Documents", description = "API for managing historical documents")
public class HistoricalContextDocumentController {
    
    private final HistoricalContextDocumentService documentService;
    
    /**
     * Get all documents
     */
    @GetMapping
    @Operation(summary = "Get all documents", description = "Retrieve list of all historical documents")
    public ResponseEntity<ApiResponse<?>> getAllDocuments() {
        log.info("GET /v1/historical-documents - Get all documents");
        List<HistoricalContextDocumentResponse> documents = documentService.getAllDocuments();
        return ResponseEntity.ok(ApiResponse.success(documents, "Retrieved successfully"));
    }
    
    /**
     * Get documents by context ID
     */
    @GetMapping("/context/{contextId}")
    @Operation(summary = "Get documents by context", description = "Get all documents for a specific historical context")
    public ResponseEntity<ApiResponse<?>> getDocumentsByContext(@PathVariable String contextId) {
        log.info("GET /v1/historical-documents/context/{} - Get documents by context", contextId);
        List<HistoricalContextDocumentResponse> documents = documentService.getDocumentsByContextId(contextId);
        return ResponseEntity.ok(ApiResponse.success(documents, "Retrieved successfully"));
    }
    
    /**
     * Get documents by staff ID (audit trail)
     */
    @GetMapping("/staff/{staffId}")
    @Operation(summary = "Get documents by staff", description = "Get all documents uploaded by a specific staff member")
    public ResponseEntity<ApiResponse<?>> getDocumentsByStaff(@PathVariable String staffId) {
        log.info("GET /v1/historical-documents/staff/{} - Get documents by staff", staffId);
        List<HistoricalContextDocumentResponse> documents = documentService.getDocumentsByStaffId(staffId);
        return ResponseEntity.ok(ApiResponse.success(documents, "Retrieved successfully"));
    }
    
    /**
     * Search documents by keyword
     */
    @GetMapping("/search")
    @Operation(summary = "Search documents", description = "Search documents by title or content")
    public ResponseEntity<ApiResponse<?>> searchDocuments(
            @RequestParam(required = false, defaultValue = "") String keyword) {
        log.info("GET /v1/historical-documents/search - Search documents with keyword: {}", keyword);
        List<HistoricalContextDocumentResponse> documents = documentService.searchDocuments(keyword);
        return ResponseEntity.ok(ApiResponse.success(documents, "Retrieved successfully"));
    }
    
    /**
     * Get single document by ID
     */
    @GetMapping("/{docId}")
    @Operation(summary = "Get document by ID", description = "Retrieve a specific historical document")
    public ResponseEntity<ApiResponse<?>> getDocumentById(@PathVariable String docId) {
        log.info("GET /v1/historical-documents/{} - Get document by ID", docId);
        HistoricalContextDocumentResponse document = documentService.getDocumentById(docId);
        return ResponseEntity.ok(ApiResponse.success(document, "Retrieved successfully"));
    }
    
    /**
     * Create/upload new document (Staff/Admin only)
     */
    @PostMapping
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Upload document", description = "Upload a new historical document (Staff/Admin only)")
    public ResponseEntity<ApiResponse<?>> createDocument(
            @Valid @RequestBody CreateHistoricalContextDocumentRequest request) {
        
        String staffId = SecurityUtils.getStaffId();
        log.info("POST /v1/historical-documents - Create document by staff: {}", staffId);
        HistoricalContextDocumentResponse document = documentService.createDocument(request, staffId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(document, "Document uploaded successfully"));
    }
    
    /**
     * Update document content/metadata (Staff/Admin only)
     */
    @PutMapping("/{docId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update document", description = "Update document content or metadata (Staff/Admin only)")
    public ResponseEntity<ApiResponse<?>> updateDocument(
            @PathVariable String docId,
            @Valid @RequestBody UpdateHistoricalContextDocumentRequest request) {
        
        String staffId = SecurityUtils.getStaffId();
        log.info("PUT /v1/historical-documents/{} - Update document by staff: {}", docId, staffId);
        HistoricalContextDocumentResponse document = documentService.updateDocument(docId, request, staffId);
        return ResponseEntity.ok(ApiResponse.success(document, "Document updated successfully"));
    }
    
    /**
     * Delete document
     */
    @DeleteMapping("/{docId}")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete document", description = "Delete a document")
    public ResponseEntity<ApiResponse<?>> deleteDocument(
            @PathVariable String docId) {
        
        String staffId = SecurityUtils.getStaffId();
        log.info("DELETE /v1/historical-documents/{} - Delete document by staff: {}", docId, staffId);
        documentService.deleteDocument(docId, staffId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
