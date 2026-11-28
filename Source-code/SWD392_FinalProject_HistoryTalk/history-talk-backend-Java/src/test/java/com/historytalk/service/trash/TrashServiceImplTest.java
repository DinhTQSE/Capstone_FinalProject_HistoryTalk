package com.historytalk.service.trash;

import com.historytalk.dto.trash.BulkTrashActionResponse;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.DocumentRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.QuizRepository;
import com.historytalk.service.historicalContext.ContextCascadeRestorer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrashServiceImplTest {

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private HistoricalContextRepository contextRepository;

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private com.historytalk.service.document.SupabaseDocumentStorageService supabaseDocumentStorageService;

    @Mock
    private com.historytalk.service.chat.AiServiceClient aiServiceClient;

    @Mock
    private ContextCascadeRestorer contextCascadeRestorer;

    @InjectMocks
    private TrashServiceImpl trashService;

    @Test
    void restoreCharacterRejectsNonTrashedRecord() {
        UUID characterId = UUID.randomUUID();
        Character character = Character.builder()
                .characterId(characterId)
                .name("Tran Hung Dao")
                .build();

        when(characterRepository.findById(characterId)).thenReturn(Optional.of(character));

        BulkTrashActionResponse response = trashService.restoreCharacters(List.of(characterId.toString()));

        assertThat(response.getSucceeded()).isZero();
        assertThat(response.getResults().get(0).getStatus()).isEqualTo("NOT_TRASHED");
    }

    @Test
    void hardDeleteCharacterAllowsTrashedRecord() {
        UUID characterId = UUID.randomUUID();
        Character character = Character.builder()
                .characterId(characterId)
                .name("Tran Hung Dao")
                .deletedAt(LocalDateTime.now(ZoneOffset.UTC))
                .build();

        when(characterRepository.findById(characterId)).thenReturn(Optional.of(character));
        when(documentRepository.findByEntityIdAndEntityType(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(chatSessionRepository.findByCharacterCharacterId(characterId)).thenReturn(List.of());

        BulkTrashActionResponse response = trashService.hardDeleteCharacters(List.of(characterId.toString()));

        assertThat(response.getSucceeded()).isEqualTo(1);
        assertThat(response.getResults().get(0).getStatus()).isEqualTo("HARD_DELETED");
        verify(characterRepository).delete(character);
    }

    @Test
    void restoreContexts_restoresChildrenWithOriginalTimestamp() {
        UUID contextId = UUID.randomUUID();
        LocalDateTime deletedAt = LocalDateTime.of(2026, 11, 28, 10, 0, 0, 123_456_000);
        HistoricalContext context = HistoricalContext.builder()
                .contextId(contextId)
                .deletedAt(deletedAt)
                .build();

        when(contextRepository.findById(contextId)).thenReturn(Optional.of(context));
        when(documentRepository.findByEntityIdAndEntityType(contextId, EntityType.CONTEXT)).thenReturn(List.of());

        BulkTrashActionResponse response = trashService.restoreContexts(List.of(contextId.toString()));

        assertThat(response.getResults().get(0).getStatus()).isEqualTo("RESTORED");
        assertThat(context.getDeletedAt()).isNull();
        verify(contextCascadeRestorer).restoreChildren(contextId, deletedAt);
    }
}
