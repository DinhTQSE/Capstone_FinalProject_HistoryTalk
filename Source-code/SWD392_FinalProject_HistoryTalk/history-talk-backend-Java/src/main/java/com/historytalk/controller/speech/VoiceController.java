package com.historytalk.controller.speech;

import com.historytalk.dto.ApiResponse;
import com.historytalk.dto.speech.VoiceOptionResponse;
import com.historytalk.dto.speech.VoicePreviewRequest;
import com.historytalk.service.speech.CharacterSpeechService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Voices that content admins and teachers choose from when creating or editing a character.
 */
@RestController
@RequestMapping("/api/v1/voices")
@RequiredArgsConstructor
@Tag(name = "Voices", description = "Character voices of the self-hosted text-to-speech server")
public class VoiceController {

    private final CharacterSpeechService characterSpeechService;

    @GetMapping
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'TEACHER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "List the voices a character can use")
    public ResponseEntity<ApiResponse<List<VoiceOptionResponse>>> listVoices() {
        return ResponseEntity.ok(ApiResponse.success(characterSpeechService.listVoices(), "Voices retrieved successfully"));
    }

    @PostMapping("/preview")
    @PreAuthorize("hasAnyRole('CONTENT_ADMIN', 'TEACHER')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Hear a voice reading the given text, or a sample sentence")
    public ResponseEntity<byte[]> previewVoice(@Valid @RequestBody VoicePreviewRequest request) {
        byte[] audio = characterSpeechService.preview(request.getVoiceId(), request.getText());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/wav"))
                .cacheControl(CacheControl.noStore())
                .body(audio);
    }
}
