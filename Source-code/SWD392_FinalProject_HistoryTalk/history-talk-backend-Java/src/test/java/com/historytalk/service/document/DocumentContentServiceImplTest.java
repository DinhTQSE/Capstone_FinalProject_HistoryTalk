package com.historytalk.service.document;

import com.historytalk.dto.document.DocumentFileResponse;
import com.historytalk.dto.document.SaveDocumentRequest;
import com.historytalk.entity.document.Document;
import com.historytalk.entity.enums.ContentStatus;
import com.historytalk.entity.enums.DocumentType;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.repository.DocumentRepository;
import com.historytalk.service.chat.AiServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentContentServiceImplTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private SupabaseDocumentStorageService supabaseDocumentStorageService;

    @Mock
    private AiServiceClient aiServiceClient;

    @InjectMocks
    private DocumentContentServiceImpl documentContentService;

    @Test
    @DisplayName("WF-02 / F-06 / DS-02a: Saving ACTIVE document commits staging images and triggers async AI embedding")
    void saveDocumentContent_ActiveStatus_CommitsStagingImagesAndTriggersEmbedding() {
        UUID docId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        Document doc = Document.builder()
                .docId(docId)
                .entityId(entityId)
                .entityType(EntityType.CONTEXT)
                .title("Chiến thắng Bạch Đằng")
                .documentType(DocumentType.TEXT)
                .status(ContentStatus.DRAFT)
                .build();

        SaveDocumentRequest request = SaveDocumentRequest.builder()
                .docId(docId.toString())
                .content("# Bạch Đằng 1288\n![Bãi cọc](https://supabase.co/staging/img1.png)")
                .status(ContentStatus.ACTIVE)
                .build();

        String committedContent = "# Bạch Đằng 1288\n![Bãi cọc](https://supabase.co/historical-images/img1.png)";
        when(documentRepository.findById(docId)).thenReturn(Optional.of(doc));
        when(supabaseDocumentStorageService.commitStagingImages(request.getContent()))
                .thenReturn(committedContent);
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        DocumentFileResponse response = documentContentService.saveDocumentContent(
                request, UUID.randomUUID().toString(), "CONTENT_ADMIN");

        assertThat(response.getDocId()).isEqualTo(docId.toString());
        assertThat(response.getType()).isEqualTo(DocumentType.MARKDOWN);
        assertThat(doc.getContent()).isEqualTo(committedContent);
        verify(aiServiceClient).processDocumentAsync(docId.toString(), entityId.toString(), committedContent);
    }

    @Test
    @DisplayName("WF-02 / F-06: Non-staff caller is rejected when saving document content")
    void saveDocumentContent_NonStaffRole_ThrowsInvalidRequestException() {
        SaveDocumentRequest request = SaveDocumentRequest.builder()
                .docId(UUID.randomUUID().toString())
                .content("Nội dung")
                .status(ContentStatus.ACTIVE)
                .build();

        assertThatThrownBy(() -> documentContentService.saveDocumentContent(
                request, UUID.randomUUID().toString(), "SCHOOL_STUDENT"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("không có quyền");

        verify(documentRepository, never()).save(any());
    }
}
