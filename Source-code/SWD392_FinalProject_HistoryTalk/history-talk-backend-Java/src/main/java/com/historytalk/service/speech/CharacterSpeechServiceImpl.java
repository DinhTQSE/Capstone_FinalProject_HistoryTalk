package com.historytalk.service.speech;

import com.historytalk.dto.speech.VoiceOptionResponse;
import com.historytalk.entity.character.Character;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.service.chat.AiServiceClient;
import com.historytalk.service.chat.AiServiceClient.SpeechStream;
import com.historytalk.service.chat.ChatContentPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Not transactional on purpose: speech takes seconds and the connection pool is small,
 * so only the character lookup touches the database.
 */
@Service
@RequiredArgsConstructor
public class CharacterSpeechServiceImpl implements CharacterSpeechService {

    static final String SAMPLE_SENTENCE =
            "Xin chào, tôi là nhân vật lịch sử. Hôm nay tôi sẽ kể cho bạn nghe về thời đại của mình.";

    private final CharacterRepository characterRepository;
    private final AiServiceClient aiServiceClient;

    /** The sample sentence of each voice never changes, so it is generated once per voice. */
    private final Map<String, byte[]> sampleCache = new ConcurrentHashMap<>();

    @Override
    public byte[] synthesize(String characterId, String text) {
        return aiServiceClient.synthesizeSpeech(text.trim(), voiceOf(characterId));
    }

    @Override
    public SpeechStream openStream(String characterId, String text) {
        return aiServiceClient.openSpeechStream(text.trim(), voiceOf(characterId));
    }

    @Override
    public List<VoiceOptionResponse> listVoices() {
        return CharacterVoices.OPTIONS.stream()
                .map(option -> VoiceOptionResponse.builder()
                        .id(option.id())
                        .gender(option.gender())
                        .region(option.region())
                        .style(option.style())
                        .recommended(option.recommended())
                        .isDefault(CharacterVoices.DEFAULT_VOICE.equals(option.id()))
                        .build())
                .toList();
    }

    @Override
    public byte[] preview(String voiceId, String text) {
        String voice = CharacterVoices.requireSupported(voiceId);
        if (voice == null) {
            throw new InvalidRequestException("Yêu cầu voiceId");
        }

        if (text == null || text.isBlank()) {
            return sampleCache.computeIfAbsent(voice, v -> aiServiceClient.synthesizeSpeech(SAMPLE_SENTENCE, v));
        }

        ChatContentPolicy.requireAppropriate(text);
        return aiServiceClient.synthesizeSpeech(text.trim(), voice);
    }

    private String voiceOf(String characterId) {
        Character character = characterRepository.findById(UUID.fromString(characterId))
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân vật với ID: " + characterId));
        return CharacterVoices.resolve(character.getVoiceId());
    }
}
