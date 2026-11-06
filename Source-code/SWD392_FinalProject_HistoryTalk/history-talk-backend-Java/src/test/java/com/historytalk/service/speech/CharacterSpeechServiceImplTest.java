package com.historytalk.service.speech;

import com.historytalk.entity.character.Character;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.service.chat.AiServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CharacterSpeechServiceImplTest {

    private static final byte[] AUDIO = {1, 2, 3};

    @Mock
    private CharacterRepository characterRepository;
    @Mock
    private AiServiceClient aiServiceClient;

    @InjectMocks
    private CharacterSpeechServiceImpl service;

    private final UUID characterId = UUID.randomUUID();

    @Test
    void characterSpeaksWithItsOwnVoice() {
        stubCharacter(Character.builder().characterId(characterId).name("Hai Bà Trưng").voiceId("Ngọc Linh").build());
        when(aiServiceClient.synthesizeSpeech("Ta là Trưng Trắc.", "Ngọc Linh")).thenReturn(AUDIO);

        assertThat(service.synthesize(characterId.toString(), "  Ta là Trưng Trắc.  ")).isEqualTo(AUDIO);
    }

    @Test
    void characterWithoutAVoiceUsesTheDefault() {
        stubCharacter(Character.builder().characterId(characterId).name("Ngô Quyền").build());
        when(aiServiceClient.synthesizeSpeech("Ta là Ngô Quyền.", CharacterVoices.DEFAULT_VOICE)).thenReturn(AUDIO);

        assertThat(service.synthesize(characterId.toString(), "Ta là Ngô Quyền.")).isEqualTo(AUDIO);
    }

    @Test
    void unknownStoredVoiceFallsBackToTheDefault() {
        stubCharacter(Character.builder().characterId(characterId).name("Ngô Quyền").voiceId("vi-VN-NamMinhNeural").build());
        when(aiServiceClient.synthesizeSpeech("Xin chào.", CharacterVoices.DEFAULT_VOICE)).thenReturn(AUDIO);

        assertThat(service.synthesize(characterId.toString(), "Xin chào.")).isEqualTo(AUDIO);
    }

    @Test
    void deletedCharacterCannotSpeak() {
        stubCharacter(Character.builder().characterId(characterId).name("Ngô Quyền").deletedAt(LocalDateTime.now()).build());

        assertThatThrownBy(() -> service.synthesize(characterId.toString(), "Xin chào."))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(aiServiceClient, never()).synthesizeSpeech(any(), any());
    }

    @Test
    void onlyPresetVoicesCanBeStored() {
        assertThat(CharacterVoices.requireSupported(" Quang Sơn ")).isEqualTo("Quang Sơn");
        assertThat(CharacterVoices.requireSupported("")).isNull();
        assertThat(CharacterVoices.requireSupported(null)).isNull();
        assertThatThrownBy(() -> CharacterVoices.requireSupported("Giọng lạ"))
                .isInstanceOf(InvalidRequestException.class);
    }

    private void stubCharacter(Character character) {
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(character));
    }
}
