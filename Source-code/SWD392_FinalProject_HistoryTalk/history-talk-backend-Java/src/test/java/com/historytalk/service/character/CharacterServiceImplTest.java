package com.historytalk.service.character;

import com.historytalk.dto.character.CharacterResponse;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.user.User;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.DocumentRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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
                .isActive(true)
                .createdBy(creator)
                .build();

        when(characterRepository.findById(characterId)).thenReturn(Optional.of(character));

        CharacterResponse response = characterService.getCharacterById(characterId.toString(), "USER");

        assertThat(response.getCharacterId()).isEqualTo(characterId.toString());
        assertThat(response.getName()).isEqualTo("Tran Hung Dao");
        assertThat(response.getStatus()).isEqualTo("ACTIVE");
    }
}
