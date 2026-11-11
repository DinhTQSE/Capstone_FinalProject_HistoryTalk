package com.historytalk.service.character;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.exception.SystemException;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.service.media.MediaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CharacterPortraitSettingsServiceImpl implements CharacterPortraitSettingsService {

    private static final String INVALID_BODY_MESSAGE =
            "Thiết lập chân dung phải là JSON object và không vượt quá 8KB";

    private final CharacterRepository characterRepository;
    private final CharacterService characterService;
    private final MediaService mediaService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public JsonNode getPortraitSettings(String characterId, String role) {
        Character character = characterService.findVisibleCharacter(characterId, role);
        String settings = character.getPortraitSettings();
        if (settings == null) {
            return null;
        }
        try {
            return objectMapper.readTree(settings);
        } catch (JsonProcessingException ex) {
            throw new SystemException("Không thể đọc thiết lập chân dung của nhân vật: " + characterId);
        }
    }

    @Override
    @Transactional
    public JsonNode savePortraitSettings(String characterId, byte[] rawBody, String userId, String userRole) {
        JsonNode settings = parseSettings(rawBody);

        UUID characterUuid = UUID.fromString(characterId);
        mediaService.assertCanManageMedia(EntityType.CHARACTER, characterUuid, userId, userRole);

        Character character = characterRepository.findById(characterUuid)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nhân vật với ID: " + characterId));

        try {
            character.setPortraitSettings(objectMapper.writeValueAsString(settings));
        } catch (JsonProcessingException ex) {
            throw new SystemException("Không thể lưu thiết lập chân dung: " + ex.getMessage());
        }
        characterRepository.save(character);
        log.info("Saved portrait settings for character {} by user {}", characterId, userId);
        return settings;
    }

    private JsonNode parseSettings(byte[] rawBody) {
        if (rawBody == null || rawBody.length == 0 || rawBody.length > MAX_SETTINGS_BYTES) {
            throw new InvalidRequestException(INVALID_BODY_MESSAGE);
        }
        JsonNode node;
        try {
            node = objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(rawBody);
        } catch (IOException ex) {
            throw new InvalidRequestException(INVALID_BODY_MESSAGE);
        }
        if (node == null || !node.isObject()) {
            throw new InvalidRequestException(INVALID_BODY_MESSAGE);
        }
        return node;
    }
}
