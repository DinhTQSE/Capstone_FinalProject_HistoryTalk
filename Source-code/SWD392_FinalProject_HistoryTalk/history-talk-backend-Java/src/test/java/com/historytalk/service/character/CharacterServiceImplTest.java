package com.historytalk.service.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.character.CharacterResponse;
import com.historytalk.dto.character.CreateCharacterRequest;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.DocumentRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharacterServiceImplTest {

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private HistoricalContextRepository contextRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private DocumentRepository documentRepository;

    @InjectMocks
    private CharacterServiceImpl characterService;

    @Test
    void getCharacterByIdReturnsPublishedActiveCharacterForPublicUser() {
        UUID characterId = UUID.randomUUID();
        User creator = User.builder()
                .uid(UUID.randomUUID())
                .userName("staff")
                .build();
        Character character = Character.builder()
                .characterId(characterId)
                .name("Tran Hung Dao")
                .background("Historical character background")
                .isPublished(true)
                .createdBy(creator)
                .build();

        when(characterRepository.findById(characterId)).thenReturn(Optional.of(character));

        CharacterResponse response = characterService.getCharacterById(characterId.toString(), "USER");

        assertThat(response.getCharacterId()).isEqualTo(characterId.toString());
        assertThat(response.getName()).isEqualTo("Tran Hung Dao");
        assertThat(response.getStatus().name()).isEqualTo("ACTIVE");
    }

    @Test
    void getAllCharactersExcludesDeletedRecordsForStaff() {
        User creator = User.builder()
                .uid(UUID.randomUUID())
                .userName("staff")
                .build();
        Character activeCharacter = Character.builder()
                .characterId(UUID.randomUUID())
                .name("Tran Hung Dao")
                .background("Historical character background")
                .isPublished(true)
                .createdBy(creator)
                .build();

        when(characterRepository.findAllWithFilter(eq(""), any(), eq(true), eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activeCharacter)));

        PaginatedResponse<CharacterResponse> response =
                characterService.getAllCharacters("", null, 1, 8, "CONTENT_ADMIN");

        assertThat(response.getContent()).hasSize(1);
        ArgumentCaptor<Boolean> includeDeletedCaptor = ArgumentCaptor.forClass(Boolean.class);
        verify(characterRepository).findAllWithFilter(eq(""), any(), eq(true), includeDeletedCaptor.capture(), any(Pageable.class));
        assertThat(includeDeletedCaptor.getValue()).isFalse();
    }

    @Test
    void characterResponseJsonDoesNotExposeDeletedAt() throws Exception {
        CharacterResponse response = CharacterResponse.builder()
                .characterId(UUID.randomUUID().toString())
                .name("Tran Hung Dao")
                .build();

        String json = new ObjectMapper().writeValueAsString(response);

        assertThat(json).doesNotContain("deletedAt");
    }

    @Test
    void createCharacterPersistsAndReturnsModelUrl() {
        UUID userId = UUID.randomUUID();
        User creator = User.builder()
                .uid(userId)
                .userName("staff")
                .build();
        CreateCharacterRequest request = CreateCharacterRequest.builder()
                .name("Tran Hung Dao")
                .background("Historical character background")
                .imageUrl("https://cdn.example.com/character.png")
                .modelUrl("https://cdn.example.com/character.glb")
                .isPublished(true)
                .build();

        when(characterRepository.existsByNameIgnoreCase("Tran Hung Dao")).thenReturn(false);
        when(userRepository.findById(userId)).thenReturn(Optional.of(creator));
        when(characterRepository.save(any(Character.class))).thenAnswer(invocation -> {
            Character character = invocation.getArgument(0);
            character.setCharacterId(UUID.randomUUID());
            return character;
        });

        CharacterResponse response = characterService.createCharacter(request, userId.toString());

        assertThat(response.getModelUrl()).isEqualTo("https://cdn.example.com/character.glb");
        ArgumentCaptor<Character> characterCaptor = ArgumentCaptor.forClass(Character.class);
        verify(characterRepository).save(characterCaptor.capture());
        assertThat(characterCaptor.getValue().getModelUrl()).isEqualTo("https://cdn.example.com/character.glb");
    }

    @Test
    void createCharacterRejectsDuplicateNameEvenIfExistingRecordIsTrashed() {
        CreateCharacterRequest request = CreateCharacterRequest.builder()
                .name("Tran Hung Dao")
                .background("Historical character background")
                .build();

        when(characterRepository.existsByNameIgnoreCase("Tran Hung Dao")).thenReturn(true);

        assertThatThrownBy(() -> characterService.createCharacter(request, UUID.randomUUID().toString()))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Character name already exists");
    }
}
