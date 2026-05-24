package com.historytalk.service.character;

import com.historytalk.dto.character.CreateCharacterDocumentRequest;
import com.historytalk.dto.character.CharacterDocumentResponse;
import com.historytalk.dto.character.UpdateCharacterDocumentRequest;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.document.Document;
import com.historytalk.entity.user.User;
import com.historytalk.entity.enums.DocumentType;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.DocumentRepository;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.service.historicalContext.strategy.DocumentProcessorFactory;
import com.historytalk.service.historicalContext.strategy.DocumentProcessorStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service layer for CharacterDocument
 * Handles business logic for document management:
 * - Create (upload)
 * - Read (list, search, get by ID)
 * - Update (replace content)
 * - Delete
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CharacterDocumentServiceImpl implements CharacterDocumentService {

    private static final long MAX_CONTENT_BYTES = 10 * 1024 * 1024; // 10MB

    private final DocumentRepository documentRepository;
    private final CharacterRepository characterRepository;
    private final UserRepository userRepository;
    private final DocumentProcessorFactory documentProcessorFactory;
    private final com.historytalk.service.chat.AiServiceClient aiServiceClient;
    
    /**
     * Get all documents
     */
    @Transactional(readOnly = true)
    public List<CharacterDocumentResponse> getAllDocuments(String userRole) {
        log.info("Fetching all historical character documents");
        boolean includeDeleted = isStaffOrAdmin(userRole);
        return documentRepository.findAllActive(EntityType.CHARACTER, includeDeleted)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Get all documents by character ID
     */
    @Transactional(readOnly = true)
    public List<CharacterDocumentResponse> getDocumentsByCharacterId(String characterId, String userRole) {
        log.info("Fetching documents for character: {}", characterId);
        boolean includeDeleted = isStaffOrAdmin(userRole);
        return documentRepository.findByEntityIdAndEntityTypeOrderByUploadDateDesc(
            UUID.fromString(characterId), EntityType.CHARACTER, includeDeleted)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Get all documents by creator user ID (audit trail)
     */
    @Transactional(readOnly = true)
    public List<CharacterDocumentResponse> getDocumentsByStaffId(String userId, String userRole) {
        log.info("Fetching documents uploaded by user: {}", userId);
        boolean includeDeleted = isStaffOrAdmin(userRole);
        return documentRepository.findByUploadedByUidAndEntityTypeOrderByUploadDateDesc(
            UUID.fromString(userId), EntityType.CHARACTER, includeDeleted)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Search documents by title or content
     */
    @Transactional(readOnly = true)
    public List<CharacterDocumentResponse> searchDocuments(String search, String userRole) {
        log.info("Searching documents with keyword: {}", search);
        boolean includeDeleted = isStaffOrAdmin(userRole);
        return documentRepository.search(normalize(search), EntityType.CHARACTER, includeDeleted)
            .stream()
            .map(this::mapToResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Get single document by ID
     */
    @Transactional(readOnly = true)
    public CharacterDocumentResponse getDocumentById(String docId, String userRole) {
        log.info("Fetching document: {}", docId);
        Document doc = documentRepository.findById(UUID.fromString(docId))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + docId));

        if (doc.getEntityType() != EntityType.CHARACTER) {
            throw new ResourceNotFoundException("Document not found: " + docId);
        }

        if (!isStaffOrAdmin(userRole) && doc.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Document not found: " + docId);
        }

        return mapToResponse(doc);
    }
    
    /**
     * Create/upload new document
     */
    @Transactional
    public CharacterDocumentResponse createDocument(CreateCharacterDocumentRequest request, String userId) {
        log.info("Creating document: {} by user: {}", request.getTitle(), userId);

        Character character = characterRepository.findById(UUID.fromString(request.getCharacterId()))
            .orElseThrow(() -> new ResourceNotFoundException("Not found resource with ID: "+request.getCharacterId().toString()));

        User user = userRepository.findById(UUID.fromString(userId))
            .orElseThrow(() -> new ResourceNotFoundException("User not found with ID: "+userId));

        DocumentType type = request.getType() != null ? request.getType() : DocumentType.TEXT;
        DocumentProcessorStrategy processor = documentProcessorFactory.getStrategy(type);
        String processedContent = processor.processContent(request.getContent());

        Document doc = Document.builder()
            .entityId(character.getCharacterId())
            .entityType(EntityType.CHARACTER)
            .createdBy(user)
            .title(request.getTitle())
            .fileUrl(request.getFileUrl())
            .content(processedContent)
            .documentType(type)
            .build();

        Document saved = documentRepository.save(doc);
        log.info("Document created: {} with ID: {}", request.getTitle(), saved.getDocId());
        
        // Trigger async document processing in AI backend
        aiServiceClient.processDocumentAsync(saved.getDocId().toString(), saved.getEntityId().toString(), saved.getContent());
        
        return mapToResponse(saved);
    }
    
    /**
     * Update document (replace content and/or title)
     */
    @Transactional
    public CharacterDocumentResponse updateDocument(String docId, UpdateCharacterDocumentRequest request, String userId, String userRole) {
        log.info("Updating document: {} by user: {}", docId, userId);
        
        Document doc = documentRepository.findById(UUID.fromString(docId))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + docId));

        if (doc.getEntityType() != EntityType.CHARACTER) {
            throw new ResourceNotFoundException("Document not found: " + docId);
        }
        
        // Staff/Admin can update any document
        if (!isStaffOrAdmin(userRole)) {
            log.warn("Unauthorized update attempt on document {} by user {}", docId, userId);
            throw new InvalidRequestException("You do not have permission to update this document");
        }
        
        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            doc.setTitle(request.getTitle());
        }

        if (request.getFileUrl() != null) {
            doc.setFileUrl(request.getFileUrl());
        }
        
        if (request.getType() != null) {
            doc.setDocumentType(request.getType());
        }
        
        if (request.getContent() != null && !request.getContent().isBlank()) {
            DocumentProcessorStrategy processor = documentProcessorFactory.getStrategy(doc.getDocumentType());
            String processedContent = processor.processContent(request.getContent());
            doc.setContent(processedContent);
        }

        Document updated = documentRepository.save(doc);
        log.info("Document updated: {}", docId);

        // If content changed, we should probably re-process the document.
        // The Python backend process endpoint handles upserts by doc_id.
        if (request.getContent() != null && !request.getContent().isBlank()) {
            aiServiceClient.processDocumentAsync(updated.getDocId().toString(), updated.getEntityId().toString(), updated.getContent());
        }

        return mapToResponse(updated);
    }
    
    /**
     * Delete document
     */
    @Transactional
    public void deleteDocument(String docId, String userId, String userRole) {
        log.info("Deleting document: {} by user: {}", docId, userId);
        
        Document doc = documentRepository.findById(UUID.fromString(docId))
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + docId));

        if (doc.getEntityType() != EntityType.CHARACTER) {
            throw new ResourceNotFoundException("Document not found: " + docId);
        }
        
        // Staff/Admin can delete any document
        if (!isStaffOrAdmin(userRole)) {
            log.warn("Unauthorized delete attempt on document {} by user {}", docId, userId);
            throw new InvalidRequestException("You do not have permission to delete this document");
        }

        documentRepository.delete(doc);
        log.info("Document deleted: {}", docId);
        
        // Trigger async document deletion in AI backend
        aiServiceClient.deleteDocumentAsync(docId);
    }
    
    /**
     * Map entity to response DTO
     */
    private CharacterDocumentResponse mapToResponse(Document doc) {
        return CharacterDocumentResponse.builder()
                .docId(doc.getDocId().toString())
                .characterId(doc.getEntityId().toString())
                .uid(doc.getCreatedBy().getUid().toString())
                .userName(doc.getCreatedBy().getUserName())
                .title(doc.getTitle())
                .content(doc.getContent())
                .fileUrl(doc.getFileUrl())
                .type(doc.getDocumentType())
                .uploadDate(doc.getUploadDate())
                .updatedDate(doc.getUpdatedDate())
                .deletedAt(doc.getDeletedAt())
                .build();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean isStaffOrAdmin(String role) {
        return role != null && (
                "CONTENT_ADMIN".equalsIgnoreCase(role)
                        || "SYSTEM_ADMIN".equalsIgnoreCase(role)
                        || "STAFF".equalsIgnoreCase(role)
                        || "ADMIN".equalsIgnoreCase(role)
        );
    }
}
