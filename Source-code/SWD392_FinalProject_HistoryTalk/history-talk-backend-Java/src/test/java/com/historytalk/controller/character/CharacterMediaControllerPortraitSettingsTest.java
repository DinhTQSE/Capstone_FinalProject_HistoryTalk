package com.historytalk.controller.character;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.exception.GlobalExceptionHandler;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.service.character.CharacterPortraitSettingsService;
import com.historytalk.service.media.MediaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CharacterMediaControllerPortraitSettingsTest {

    private static final String BASE = "/api/v1/characters/{characterId}/media/portrait-settings";
    private static final String EXAMPLE_BODY = """
            {"mode":"AUTO","mouthOpen":1,"zoom":1,
             "points":{"eyeLeft":[0.45,0.25],"eyeRight":[0.55,0.25],"mouth":[0.5,0.33],"chin":[0.5,0.38]},
             "imageUrl":"https://example.com/portrait.png"}
            """;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;

    @Mock
    private MediaService mediaService;

    @Mock
    private CharacterPortraitSettingsService portraitSettingsService;

    @InjectMocks
    private CharacterMediaController controller;

    private final String characterId = UUID.randomUUID().toString();

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void get_unsaved_returnsSuccessWithNullData() throws Exception {
        when(portraitSettingsService.getPortraitSettings(eq(characterId), any())).thenReturn(null);

        mockMvc.perform(get(BASE, characterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(content().string(containsString("\"data\":null")));
    }

    @Test
    void get_saved_returnsJsonObject() throws Exception {
        when(portraitSettingsService.getPortraitSettings(eq(characterId), any()))
                .thenReturn(objectMapper.readTree(EXAMPLE_BODY));

        mockMvc.perform(get(BASE, characterId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("AUTO"))
                .andExpect(jsonPath("$.data.points.mouth[0]").value(0.5))
                .andExpect(jsonPath("$.message").value("Portrait settings retrieved successfully"));
    }

    @Test
    void get_characterMissing_returns404() throws Exception {
        when(portraitSettingsService.getPortraitSettings(eq(characterId), any()))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy nhân vật với id: " + characterId));

        mockMvc.perform(get(BASE, characterId))
                .andExpect(status().isNotFound());
    }

    @Test
    void get_invalidUuid_returns400() throws Exception {
        when(portraitSettingsService.getPortraitSettings(eq("not-a-uuid"), any()))
                .thenThrow(new IllegalArgumentException("Invalid UUID string: not-a-uuid"));

        mockMvc.perform(get(BASE, "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void put_validBody_passesRawBytesAndReturnsSavedJson() throws Exception {
        byte[] body = EXAMPLE_BODY.getBytes(StandardCharsets.UTF_8);
        when(portraitSettingsService.savePortraitSettings(eq(characterId), eq(body), any(), any()))
                .thenReturn(objectMapper.readTree(body));

        mockMvc.perform(put(BASE, characterId).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.mode").value("AUTO"))
                .andExpect(jsonPath("$.message").value("Portrait settings saved successfully"));

        verify(portraitSettingsService).savePortraitSettings(eq(characterId), eq(body), any(), any());
    }

    @Test
    void put_invalidBody_returns400() throws Exception {
        when(portraitSettingsService.savePortraitSettings(eq(characterId), any(), any(), any()))
                .thenThrow(new InvalidRequestException("Thiết lập chân dung phải là JSON object và không vượt quá 8KB"));

        mockMvc.perform(put(BASE, characterId).contentType(MediaType.APPLICATION_JSON).content("[1,2]"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void put_nonJsonContentType_returns415() throws Exception {
        mockMvc.perform(put(BASE, characterId).contentType(MediaType.TEXT_PLAIN).content(EXAMPLE_BODY))
                .andExpect(status().isUnsupportedMediaType());

        verifyNoInteractions(portraitSettingsService);
    }

    @Test
    void put_isRestrictedToContentAdminSystemAdminTeacher() throws Exception {
        PreAuthorize preAuthorize = CharacterMediaController.class
                .getMethod("savePortraitSettings", String.class, byte[].class)
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNotNull();
        assertThat(preAuthorize.value()).isEqualTo("hasAnyRole('CONTENT_ADMIN', 'SYSTEM_ADMIN', 'TEACHER')");
    }

    @Test
    void get_hasNoPreAuthorize() throws Exception {
        PreAuthorize preAuthorize = CharacterMediaController.class
                .getMethod("getPortraitSettings", String.class)
                .getAnnotation(PreAuthorize.class);

        assertThat(preAuthorize).isNull();
    }
}
