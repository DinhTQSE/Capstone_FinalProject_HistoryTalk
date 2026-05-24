package com.historytalk.controller.character;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.character.CreateCharacterDocumentRequest;
import com.historytalk.dto.character.CharacterDocumentResponse;
import com.historytalk.dto.character.UpdateCharacterDocumentRequest;
import com.historytalk.service.character.CharacterDocumentService;
import com.historytalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Historical Character Document Management
 * 
 * Endpoints:
 * - GET /v1/character-documents - Get all documents (public)
 * - GET /v1/character-documents/{docId} - Get single document (public)
 * - GET /v1/character-documents/search - Search documents (public)
 * - POST /v1/character-documents - Upload/create document (Staff/Admin)
 * - PUT /v1/character-documents/{docId} - Update document (Staff/Admin)
 * - DELETE /v1/character-documents/{docId} - Remove document (Staff/Admin)
 */
@RestController
@RequestMapping("/api/v1/character-documents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Historical Character Documents", description = "API for managing character documents")
public class CharacterDocumentController {
    
    private final CharacterDocumentService documentService;
    
    /**
     * Get all documents
     */
    @GetMapping
    @Operation(summary = "Get all documents", description = "Retrieve list of all character documents")
    public ResponseEntity<ApiResponse<?>> getAllDocuments() {
        log.info("GET /v1/character-documents - Get all documents");
        String userRole = SecurityUtils.getRoleName();
        List<CharacterDocumentResponse> documents = documentService.getAllDocuments(userRole);
        return ResponseEntity.ok(ApiResponse.success(documents, "Retrieved successfully"));
    }
    
    /**
     * Get documents by character ID
     */
    @GetMapping("/character/{characterId}")
    @Operation(summary = "Get documents by character", description = "Get all documents for a specific historical character")
    public ResponseEntity<ApiResponse<?>> getDocumentsByCharacter(@PathVariable String characterId) {
        log.info("GET /v1/character-documents/character/{} - Get documents by character", characterId);
        String userRole = SecurityUtils.getRoleName();
        List<CharacterDocumentResponse> documents = documentService.getDocumentsByCharacterId(characterId, userRole);
        return ResponseEntity.ok(ApiResponse.success(documents, "Retrieved successfully"));
    }
    
    /**
     * Get documents by staff ID (audit trail)
     */
    @GetMapping("/staff/{staffId}")
    @Operation(summary = "Get documents by staff", description = "Get all documents uploaded by a specific staff member")
    public ResponseEntity<ApiResponse<?>> getDocumentsByStaff(@PathVariable String staffId) {
        log.info("GET /v1/character-documents/staff/{} - Get documents by staff", staffId);
        String userRole = SecurityUtils.getRoleName();
        List<CharacterDocumentResponse> documents = documentService.getDocumentsByStaffId(staffId, userRole);
        return ResponseEntity.ok(ApiResponse.success(documents, "Retrieved successfully"));
    }
    
    /**
     * Search documents by keyword
     */
    @GetMapping("/search")
    @Operation(summary = "Search documents", description = "Search documents by title or content")
    public ResponseEntity<ApiResponse<?>> searchDocuments(
            @RequestParam(required = false, defaultValue = "") String keyword) {
        log.info("GET /v1/character-documents/search - Search documents with keyword: {}", keyword);
        String userRole = SecurityUtils.getRoleName();
        List<CharacterDocumentResponse> documents = documentService.searchDocuments(keyword, userRole);
        return ResponseEntity.ok(ApiResponse.success(documents, "Retrieved successfully"));
    }
    
    /**
     * Get single document by ID
     */
    @GetMapping("/{docId}")
    @Operation(summary = "Get document by ID", description = "Retrieve a specific historical document")
    public ResponseEntity<ApiResponse<?>> getDocumentById(@PathVariable String docId) {
        log.info("GET /v1/character-documents/{} - Get document by ID", docId);
        String userRole = SecurityUtils.getRoleName();
        CharacterDocumentResponse document = documentService.getDocumentById(docId, userRole);
        return ResponseEntity.ok(ApiResponse.success(document, "Retrieved successfully"));
    }
    
    /**
     * Create/upload new document (Staff/Admin only)
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Upload document", description = "Upload a new historical document (Staff/Admin only)")
    public ResponseEntity<ApiResponse<?>> createDocument(
            @Valid @RequestBody CreateCharacterDocumentRequest request) {
        
        String staffId = SecurityUtils.getUserId();
        log.info("POST /v1/character-documents - Create document by user: {}", staffId);
        CharacterDocumentResponse document = documentService.createDocument(request, staffId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(document, "Document uploaded successfully"));
    }
    
    /**
     * Update document content/metadata (Staff/Admin only)
     */
    @PutMapping("/{docId}")
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update document", description = "Update document content or metadata (Staff/Admin only)")
    public ResponseEntity<ApiResponse<?>> updateDocument(
            @PathVariable String docId,
            @Valid @RequestBody UpdateCharacterDocumentRequest request) {
        
        String staffId = SecurityUtils.getUserId();
        String staffRole = SecurityUtils.getRoleName();
        log.info("PUT /v1/character-documents/{} - Update document by user: {}", docId, staffId);
        CharacterDocumentResponse document = documentService.updateDocument(docId, request, staffId, staffRole);
        return ResponseEntity.ok(ApiResponse.success(document, "Document updated successfully"));
    }
    
    /**
     * Delete document
     */
    @DeleteMapping("/{docId}")
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete document", description = "Delete a document")
    public ResponseEntity<ApiResponse<?>> deleteDocument(
            @PathVariable String docId) {
        
        String staffId = SecurityUtils.getUserId();
        String staffRole = SecurityUtils.getRoleName();
        log.info("DELETE /v1/character-documents/{} - Delete document by user: {}", docId, staffId);
        documentService.deleteDocument(docId, staffId, staffRole);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
