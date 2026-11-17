package com.historytalk.controller.speech;

import com.historytalk.dto.speech.VoiceOptionResponse;
import com.historytalk.exception.GlobalExceptionHandler;
import com.historytalk.service.speech.CharacterSpeechService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VoiceControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CharacterSpeechService characterSpeechService;

    @InjectMocks
    private VoiceController voiceController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(voiceController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("IT-VOICE-01 (WF-03 / F-04): GET /api/v1/voices returns available character TTS voices")
    void listVoices_Returns200() throws Exception {
        VoiceOptionResponse voice = VoiceOptionResponse.builder()
                .id("nam-bac-tram")
                .gender("MALE")
                .region("NORTH")
                .style("Trầm hùng")
                .recommended(true)
                .isDefault(true)
                .build();

        when(characterSpeechService.listVoices()).thenReturn(List.of(voice));

        mockMvc.perform(get("/api/v1/voices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value("nam-bac-tram"))
                .andExpect(jsonPath("$.data[0].gender").value("MALE"));
    }

    @Test
    @DisplayName("IT-VOICE-02 (WF-03 / F-04): POST /api/v1/voices/preview returns synthesized WAV audio bytes")
    void previewVoice_ValidRequest_ReturnsAudioWav() throws Exception {
        byte[] wavBytes = new byte[]{'R', 'I', 'F', 'F', 0, 1, 2, 3};
        when(characterSpeechService.preview(eq("nam-bac-tram"), eq("Ta thà làm quỷ nước Nam")))
                .thenReturn(wavBytes);

        String body = "{\"voiceId\":\"nam-bac-tram\",\"text\":\"Ta thà làm quỷ nước Nam\"}";

        mockMvc.perform(post("/api/v1/voices/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(content().contentType("audio/wav"))
                .andExpect(content().bytes(wavBytes));
    }
}
