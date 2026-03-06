package com.historyTalk.service.historicalContext;

import com.historyTalk.dto.historicalContext.CreateHistoricalContextDocumentRequest;
import com.historyTalk.dto.historicalContext.HistoricalContextDocumentResponse;
import com.historyTalk.dto.historicalContext.UpdateHistoricalContextDocumentRequest;
import com.historyTalk.entity.historicalContext.HistoricalContext;
import com.historyTalk.entity.historicalContext.HistoricalContextDocument;
import com.historyTalk.entity.staff.Staff;
import com.historyTalk.exception.InvalidRequestException;
import com.historyTalk.exception.ResourceNotFoundException;
import com.historyTalk.repository.HistoricalContextDocumentRepository;
import com.historyTalk.repository.HistoricalContextRepository;
import com.historyTalk.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for HistoricalContextDocument
 * Handles business logic for document management:
 * - Create (upload)
 * - Read (list, search, get by ID)
 * - Update (replace content)
 * - Delete
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HistoricalContextDocumentService {

    private static final long MAX_CONTENT_BYTES = 10 * 1024 * 1024; // 10MB

    private final HistoricalContextDocumentRepository documentRepository;
    private final HistoricalContextRepository contextRepository;
    private final StaffRepository staffRepository;
    
    /**
     * Get all documents
     */
    @Transactional(readOnly = true)
    public List<HistoricalContextDocumentResponse> getAllDocuments() {
        log.info("Fetching all historical context documents");
        return documentRepository.findAllByOrderByUploadDateDesc()
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Get all documents by context ID
     */
    @Transactional(readOnly = true)
    public List<HistoricalContextDocumentResponse> getDocumentsByContextId(String contextId) {
        log.info("Fetching documents for context: {}", contextId);
        return documentRepository.findByHistoricalContextContextIdOrderByUploadDateDesc(UUID.fromString(contextId))
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Get all documents by staff ID (audit trail)
     */
    @Transactional(readOnly = true)
    public List<HistoricalContextDocumentResponse> getDocumentsByStaffId(String staffId) {
        log.info("Fetching documents uploaded by staff: {}", staffId);
        return documentRepository.findByStaffStaffIdOrderByUploadDateDesc(UUID.fromString(staffId))
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Search documents by title or content
     */
    @Transactional(readOnly = true)
    public List<HistoricalContextDocumentResponse> searchDocuments(String search) {
        log.info("Searching documents with keyword: {}", search);
        return documentRepository.search(normalize(search))
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Get single document by ID
     */
    @Transactional(readOnly = true)
    public HistoricalContextDocumentResponse getDocumentById(String docId) {
        log.info("Fetching document: {}", docId);
        HistoricalContextDocument doc = documentRepository.findById(UUID.fromString(docId))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + docId));
        return mapToResponse(doc);
    }
    
    /**
     * Create/upload new document
     */
    @Transactional
    public HistoricalContextDocumentResponse createDocument(CreateHistoricalContextDocumentRequest request, String staffId) {
        log.info("Creating document: {} by staff: {}", request.getTitle(), staffId);

        HistoricalContext context = contextRepository.findById(UUID.fromString(request.getContextId()))
            .orElseThrow(() -> new ResourceNotFoundException("Not found resource with ID: "+request.getContextId().toString()));

        Staff staff = staffRepository.findById(UUID.fromString(staffId))
            .orElseThrow(() -> new ResourceNotFoundException("Not found staff with ID: "+staffId));

        validateContent(request.getContent());

        HistoricalContextDocument doc = HistoricalContextDocument.builder()
            .historicalContext(context)
            .staff(staff)
            .title(request.getTitle())
            .content(request.getContent())
            .build();

        HistoricalContextDocument saved = documentRepository.save(doc);
        log.info("Document created: {} with ID: {}", request.getTitle(), saved.getDocId());
        return mapToResponse(saved);
    }
    
    /**
     * Update document (replace content and/or title)
     */
    @Transactional
    public HistoricalContextDocumentResponse updateDocument(String docId, UpdateHistoricalContextDocumentRequest request, String staffId) {
        log.info("Updating document: {} by staff: {}", docId, staffId);
        
        HistoricalContextDocument doc = documentRepository.findById(UUID.fromString(docId))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + docId));
        
        // Only creator or admin can update
        if (!doc.getStaff().getStaffId().equals(UUID.fromString(staffId))) {
            log.warn("Unauthorized update attempt on document {} by staff {}", docId, staffId);
            throw new InvalidRequestException("Only document creator can update this record");
        }
        
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            doc.setTitle(request.getTitle());
        }
        
        if (request.getContent() != null && !request.getContent().isBlank()) {
            validateContent(request.getContent());
            doc.setContent(request.getContent());
        }
        if (request.getEra() != null) {
            doc.setEra(request.getEra());
        }
        if (request.getCategory() != null) {
            doc.setCategory(request.getCategory());
        }
        if (request.getYear() != null) {
            doc.setYear(request.getYear());
        }
        if (request.getStartYear() != null) {
            doc.setStartYear(request.getStartYear());
        }
        if (request.getEndYear() != null) {
            doc.setEndYear(request.getEndYear());
        }

        HistoricalContextDocument updated = documentRepository.save(doc);
        log.info("Document updated: {}", docId);
        return mapToResponse(updated);
    }
    
    /**
     * Delete document
     */
    @Transactional
    public void deleteDocument(String docId, String staffId) {
        log.info("Deleting document: {} by staff: {}", docId, staffId);
        
        HistoricalContextDocument doc = documentRepository.findById(UUID.fromString(docId))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + docId));
        
        // Only creator or admin can delete
        if (!doc.getStaff().getStaffId().equals(UUID.fromString(staffId))) {
            log.warn("Unauthorized delete attempt on document {} by staff {}", docId, staffId);
            throw new InvalidRequestException("Only document creator can delete this record");
        }

        documentRepository.delete(doc);
        log.info("Document deleted: {}", docId);
    }
    
    /**
     * Map entity to response DTO
     */
    private HistoricalContextDocumentResponse mapToResponse(HistoricalContextDocument doc) {
        return HistoricalContextDocumentResponse.builder()
                .docId(doc.getDocId().toString())
                .contextId(doc.getHistoricalContext().getContextId().toString())
                .staffId(doc.getStaff().getStaffId().toString())
                .staffName(doc.getStaff().getName())
                .title(doc.getTitle())
                .content(doc.getContent())
                .uploadDate(doc.getUploadDate())
                .updatedDate(doc.getUpdatedDate())
                .build();
    }

    private void validateContent(String content) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Content cannot be blank");
        }
        long contentSize = content.getBytes(StandardCharsets.UTF_8).length;
        if (contentSize > MAX_CONTENT_BYTES) {
            log.warn("Content too large: {} bytes", contentSize);
            throw new IllegalArgumentException("Content size exceeds 10MB limit");
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
