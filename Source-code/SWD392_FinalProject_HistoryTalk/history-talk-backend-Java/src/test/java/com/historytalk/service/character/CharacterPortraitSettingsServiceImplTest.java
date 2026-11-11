package com.historytalk.service.character;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.enums.EntityType;
import com.historytalk.exception.ForbiddenException;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.exception.UnauthorizedException;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.service.media.MediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CharacterPortraitSettingsServiceImplTest {

    private static final String EXAMPLE_BODY = """
            {
              "mode": "AUTO",
              "mouthOpen": 1,
              "zoom": 1,
              "points": {
                "eyeLeft": [0.45, 0.25],
                "eyeRight": [0.55, 0.25],
                "mouth": [0.5, 0.33],
                "chin": [0.5, 0.38]
              },
              "imageUrl": "https://example.com/portrait.png"
            }
            """;

    @Mock
    private CharacterRepository characterRepository;

    @Mock
    private CharacterService characterService;

    @Mock
    private MediaService mediaService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private CharacterPortraitSettingsServiceImpl service;

    private UUID characterUuid;
    private String characterId;
    private Character character;
    private final String userId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        service = new CharacterPortraitSettingsServiceImpl(characterRepository, characterService, mediaService, objectMapper);
        characterUuid = UUID.randomUUID();
        characterId = characterUuid.toString();
        character = Character.builder().characterId(characterUuid).name("Trần Hưng Đạo").build();
        when(characterRepository.findById(characterUuid)).thenReturn(Optional.of(character));
        when(characterService.findVisibleCharacter(eq(characterId), any())).thenReturn(character);
    }

    @Test
    void get_noSavedSettings_returnsNull() {
        assertThat(service.getPortraitSettings(characterId, null)).isNull();
    }

    @Test
    void get_savedSettings_returnsJsonObject() {
        character.setPortraitSettings("{\"mode\":\"AUTO\",\"zoom\":1}");

        JsonNode node = service.getPortraitSettings(characterId, null);

        assertThat(node.isObject()).isTrue();
        assertThat(node.get("mode").asText()).isEqualTo("AUTO");
    }

    @Test
    void get_hiddenCharacter_throwsNotFound() {
        when(characterService.findVisibleCharacter(eq(characterId), any()))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy nhân vật với id: " + characterId));

        assertThatThrownBy(() -> service.getPortraitSettings(characterId, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void save_validObject_persistsAndReturnsSameJson() throws Exception {
        byte[] body = EXAMPLE_BODY.getBytes(StandardCharsets.UTF_8);

        JsonNode out = service.savePortraitSettings(characterId, body, userId, "CONTENT_ADMIN");

        verify(mediaService).assertCanManageMedia(EntityType.CHARACTER, characterUuid, userId, "CONTENT_ADMIN");
        ArgumentCaptor<Character> captor = ArgumentCaptor.forClass(Character.class);
        verify(characterRepository).save(captor.capture());
        assertThat(objectMapper.readTree(captor.getValue().getPortraitSettings()))
                .isEqualTo(objectMapper.readTree(body));
        assertThat(out).isEqualTo(objectMapper.readTree(body));
    }

    @Test
    void save_acceptsExactly8192Bytes() {
        byte[] body = paddedObject(CharacterPortraitSettingsService.MAX_SETTINGS_BYTES, "a");
        assertThat(body).hasSize(8192);

        service.savePortraitSettings(characterId, body, userId, "CONTENT_ADMIN");

        verify(characterRepository).save(any());
    }

    @Test
    void save_rejects8193Bytes() {
        // "ệ" is 3 bytes in UTF-8, so the body has far fewer than 8193 characters.
        byte[] body = paddedObject(CharacterPortraitSettingsService.MAX_SETTINGS_BYTES + 1, "ệ");
        assertThat(body).hasSize(8193);
        assertThat(new String(body, StandardCharsets.UTF_8).length()).isLessThan(8193);

        assertThatThrownBy(() -> service.savePortraitSettings(characterId, body, userId, "CONTENT_ADMIN"))
                .isInstanceOf(InvalidRequestException.class);
        verify(characterRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1,2]", "\"text\"", "null", "42", "{\"a\":1} xyz", "{\"a\":", "", "   "})
    void save_rejectsNonObjectOrMalformedJson(String raw) {
        byte[] body = raw.getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.savePortraitSettings(characterId, body, userId, "CONTENT_ADMIN"))
                .isInstanceOf(InvalidRequestException.class);
        verify(characterRepository, never()).save(any());
    }

    @Test
    void save_nullBody_throwsInvalidRequest() {
        assertThatThrownBy(() -> service.savePortraitSettings(characterId, null, userId, "CONTENT_ADMIN"))
                .isInstanceOf(InvalidRequestException.class);
        verify(characterRepository, never()).save(any());
    }

    @Test
    void save_teacherNotOwner_throwsForbidden403_andDoesNotSave() {
        doThrow(new UnauthorizedException("You can only modify content you created"))
                .when(mediaService).assertCanManageMedia(eq(EntityType.CHARACTER), eq(characterUuid), anyString(), eq("TEACHER"));

        // A logged-in caller lacking permission is 403, not 401 (FE treats 401 as an expired session).
        assertThatThrownBy(() -> service.savePortraitSettings(
                characterId, EXAMPLE_BODY.getBytes(StandardCharsets.UTF_8), userId, "TEACHER"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("You can only modify content you created")
                .extracting(ex -> ((ForbiddenException) ex).getHttpStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
        verify(characterRepository, never()).save(any());
    }

    @Test
    void save_softDeletedCharacter_throwsNotFound() {
        character.setDeletedAt(LocalDateTime.now());

        assertThatThrownBy(() -> service.savePortraitSettings(
                characterId, EXAMPLE_BODY.getBytes(StandardCharsets.UTF_8), userId, "CONTENT_ADMIN"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(characterRepository, never()).save(any());
    }

    /** Builds {"pad":"xxx..."} whose UTF-8 encoding is exactly {@code totalBytes} long. */
    private static byte[] paddedObject(int totalBytes, String unit) {
        int unitBytes = unit.getBytes(StandardCharsets.UTF_8).length;
        String prefix = "{\"pad\":\"";
        String suffix = "\"}";
        int remaining = totalBytes - prefix.length() - suffix.length();
        StringBuilder sb = new StringBuilder(prefix);
        sb.append(unit.repeat(remaining / unitBytes));
        sb.append("a".repeat(remaining % unitBytes));
        sb.append(suffix);
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
