package com.historytalk.service.document;

import com.historytalk.dto.document.DocumentFileResponse;
import com.historytalk.entity.document.Document;
import com.historytalk.entity.enums.DocumentType;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentFileServiceImpl implements DocumentFileService {

    private static final long PDF_URL_EXPIRES_IN_SECONDS = 300;

    private final DocumentRepository documentRepository;
    private final SupabaseDocumentStorageService supabaseDocumentStorageService;

    @Override
    @Transactional
    public DocumentFileResponse uploadPdfFile(String docId, MultipartFile file, String userId, String userRole) {
        if (!isStaffOrAdmin(userRole)) {
            throw new InvalidRequestException("Bạn không có quyền tải file tài liệu này lên");
        }

        Document doc = documentRepository.findById(UUID.fromString(docId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu: " + docId));

        UploadedDocumentFile uploaded = supabaseDocumentStorageService.uploadPdf(
                doc.getEntityType(),
                doc.getEntityId(),
                doc.getDocId(),
                file);

        doc.setFileUrl(uploaded.objectPath());
        doc.setDocumentType(DocumentType.PDF);

        Document saved = documentRepository.save(doc);
        log.info("PDF file uploaded for document {}", saved.getDocId());
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadedDocumentFile downloadPdfFile(String docId, String userId, String userRole) {
        if (!isStaffOrAdmin(userRole)) {
            throw new InvalidRequestException("Bạn không có quyền tải xuống file tài liệu này");
        }

        Document doc = documentRepository.findById(UUID.fromString(docId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu: " + docId));

        if (doc.getDocumentType() != DocumentType.PDF || doc.getFileUrl() == null || doc.getFileUrl().isBlank()) {
            throw new InvalidRequestException("Tài liệu không có file PDF nào được tải lên");
        }

        return supabaseDocumentStorageService.downloadPdf(doc.getFileUrl());
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentPdfUrl createPdfUrl(String docId, String userId, String userRole) {
        if (!isStaffOrAdmin(userRole)) {
            throw new InvalidRequestException("Bạn không có quyền tải xuống file tài liệu này");
        }

        Document doc = documentRepository.findById(UUID.fromString(docId))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài liệu: " + docId));

        if (doc.getDocumentType() != DocumentType.PDF || doc.getFileUrl() == null || doc.getFileUrl().isBlank()) {
            throw new InvalidRequestException("Tài liệu không có file PDF nào được tải lên");
        }

        return supabaseDocumentStorageService.createSignedPdfUrl(
                doc.getFileUrl(),
                PDF_URL_EXPIRES_IN_SECONDS,
                doc.getDocId() + ".pdf");
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
