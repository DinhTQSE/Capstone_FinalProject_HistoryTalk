package com.historytalk.service.document;

import com.historytalk.dto.document.DocumentFileResponse;
import com.historytalk.entity.document.Document;
import com.historytalk.entity.enums.DocumentType;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentFileServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private SupabaseDocumentStorageService storageService;

    @InjectMocks
    private DocumentFileServiceImpl service;

    @Test
    void uploadPdfFileUpdatesFileUrlAndTypeWithoutChangingContent() {
        UUID docId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        Document document = Document.builder()
                .docId(docId)
                .entityId(entityId)
                .entityType(EntityType.CONTEXT)
                .title("Primary source")
                .content("Existing text content")
                .documentType(DocumentType.TEXT)
                .build();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "source.pdf",
                "application/pdf",
                "pdf-bytes".getBytes());

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(storageService.uploadPdf(EntityType.CONTEXT, entityId, docId, file))
                .thenReturn(new UploadedDocumentFile("documents/context/" + entityId + "/" + docId + ".pdf"));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DocumentFileResponse response = service.uploadPdfFile(
                docId.toString(),
                file,
                UUID.randomUUID().toString(),
                "CONTENT_ADMIN");

        assertThat(response.getDocId()).isEqualTo(docId.toString());
        assertThat(response.getEntityId()).isEqualTo(entityId.toString());
        assertThat(response.getEntityType()).isEqualTo(EntityType.CONTEXT);
        assertThat(response.getFileUrl()).isEqualTo("documents/context/" + entityId + "/" + docId + ".pdf");
        assertThat(response.getType()).isEqualTo(DocumentType.PDF);
        assertThat(document.getContent()).isEqualTo("Existing text content");

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(documentCaptor.capture());
        assertThat(documentCaptor.getValue().getContent()).isEqualTo("Existing text content");
        assertThat(documentCaptor.getValue().getFileUrl()).isEqualTo("documents/context/" + entityId + "/" + docId + ".pdf");
        assertThat(documentCaptor.getValue().getDocumentType()).isEqualTo(DocumentType.PDF);
    }

    @Test
    void uploadPdfFileRejectsNonStaffRoleBeforeLoadingDocument() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "source.pdf",
                "application/pdf",
                "pdf-bytes".getBytes());

        assertThatThrownBy(() -> service.uploadPdfFile(UUID.randomUUID().toString(), file, UUID.randomUUID().toString(), "USER"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("permission");

        verifyNoInteractions(documentRepository, storageService);
    }

    @Test
    void uploadPdfFileThrowsNotFoundWhenDocumentDoesNotExist() {
        UUID docId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "source.pdf",
                "application/pdf",
                "pdf-bytes".getBytes());

        when(documentRepository.findById(docId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.uploadPdfFile(docId.toString(), file, UUID.randomUUID().toString(), "CONTENT_ADMIN"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Document not found");

        verify(storageService, never()).uploadPdf(any(), any(), any(), any());
    }

    @Test
    void downloadPdfFileDownloadsStoredPdfWithoutChangingDocument() {
        UUID docId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        Document document = Document.builder()
                .docId(docId)
                .entityId(entityId)
                .entityType(EntityType.CHARACTER)
                .title("Character source")
                .content("Existing content")
                .fileUrl("documents/character/" + entityId + "/" + docId + ".pdf")
                .documentType(DocumentType.PDF)
                .build();
        byte[] bytes = "pdf-bytes".getBytes();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(storageService.downloadPdf(document.getFileUrl()))
                .thenReturn(new DownloadedDocumentFile(bytes, "application/pdf"));

        DownloadedDocumentFile response = service.downloadPdfFile(
                docId.toString(),
                UUID.randomUUID().toString(),
                "SYSTEM_ADMIN");

        assertThat(response.bytes()).isEqualTo(bytes);
        assertThat(response.contentType()).isEqualTo("application/pdf");
        assertThat(document.getContent()).isEqualTo("Existing content");
        verify(storageService).downloadPdf("documents/character/" + entityId + "/" + docId + ".pdf");
        verify(documentRepository, never()).save(any(Document.class));
    }

    @Test
    void downloadPdfFileRejectsDocumentWithoutPdfFile() {
        UUID docId = UUID.randomUUID();
        Document document = Document.builder()
                .docId(docId)
                .entityId(UUID.randomUUID())
                .entityType(EntityType.CONTEXT)
                .title("Text source")
                .documentType(DocumentType.TEXT)
                .build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));

        assertThatThrownBy(() -> service.downloadPdfFile(docId.toString(), UUID.randomUUID().toString(), "CONTENT_ADMIN"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("uploaded PDF");

        verify(storageService, never()).downloadPdf(any());
    }

    @Test
    void createPdfUrlReturnsSignedUrlForUploadedPdf() {
        UUID docId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        Document document = Document.builder()
                .docId(docId)
                .entityId(entityId)
                .entityType(EntityType.CONTEXT)
                .title("PDF source")
                .fileUrl("documents/context/" + entityId + "/" + docId + ".pdf")
                .documentType(DocumentType.PDF)
                .build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(storageService.createSignedPdfUrl(document.getFileUrl(), 300, docId + ".pdf"))
                .thenReturn(new DocumentPdfUrl("https://example.supabase.co/signed.pdf", 300));

        DocumentPdfUrl response = service.createPdfUrl(docId.toString(), UUID.randomUUID().toString(), "CONTENT_ADMIN");

        assertThat(response.url()).isEqualTo("https://example.supabase.co/signed.pdf");
        assertThat(response.expiresIn()).isEqualTo(300);
        verify(storageService).createSignedPdfUrl("documents/context/" + entityId + "/" + docId + ".pdf", 300, docId + ".pdf");
    }
}
