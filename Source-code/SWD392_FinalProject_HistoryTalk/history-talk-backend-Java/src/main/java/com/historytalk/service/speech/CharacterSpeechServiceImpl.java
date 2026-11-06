package com.historytalk.service.speech;

import com.historytalk.entity.character.Character;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.service.chat.AiServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Not transactional on purpose: speech takes seconds and the connection pool is small,
 * so only the character lookup touches the database.
 */
@Service
@RequiredArgsConstructor
public class CharacterSpeechServiceImpl implements CharacterSpeechService {

    private final CharacterRepository characterRepository;
    private final AiServiceClient aiServiceClient;

    @Override
    public byte[] synthesize(String characterId, String text) {
        Character character = characterRepository.findById(UUID.fromString(characterId))
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân vật với ID: " + characterId));

        return aiServiceClient.synthesizeSpeech(text.trim(), CharacterVoices.resolve(character.getVoiceId()));
    }
}
