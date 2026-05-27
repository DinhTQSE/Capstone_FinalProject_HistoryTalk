package com.historytalk.service.trash;

import com.historytalk.dto.trash.BulkTrashActionResponse;
import com.historytalk.entity.character.Character;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.ChatSessionRepository;
import com.historytalk.repository.DocumentRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.QuizRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
                .deletedAt(LocalDateTime.now())
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
}
