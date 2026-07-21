package com.historytalk.service.document;

import com.historytalk.dto.document.DocumentFileResponse;
import com.historytalk.dto.document.SaveDocumentRequest;
import com.historytalk.entity.document.Document;
import com.historytalk.entity.enums.ContentStatus;
import com.historytalk.entity.enums.DocumentType;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentContentServiceImpl implements DocumentContentService {

    private final DocumentRepository documentRepository;

    @Override
    @Transactional
    public DocumentFileResponse saveDocumentContent(SaveDocumentRequest request, String userId, String userRole) {
        if (!isStaffOrAdmin(userRole)) {
            throw new InvalidRequestException("Bạn không có quyền lưu nội dung tài liệu này");
        }

        Document doc = documentRepository.findById(UUID.fromString(request.getDocId()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu: " + request.getDocId()));

        doc.setContent(request.getContent());
        doc.setStatus(request.getStatus());
        
        if (doc.getDocumentType() == DocumentType.TEXT) {
            doc.setDocumentType(DocumentType.MARKDOWN);
        }

        Document saved = documentRepository.save(doc);
        log.info("Document content saved for document {} with status {}", saved.getDocId(), saved.getStatus());
        
        return mapToResponse(saved);
    }

    private DocumentFileResponse mapToResponse(Document doc) {
        return DocumentFileResponse.builder()
                .docId(doc.getDocId().toString())
                .entityId(doc.getEntityId().toString())
                .entityType(doc.getEntityType())
                .title(doc.getTitle())
                .fileUrl(doc.getFileUrl())
                .type(doc.getDocumentType())
                .uploadDate(doc.getUploadDate())
                .updatedDate(doc.getUpdatedDate())
                .build();
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
