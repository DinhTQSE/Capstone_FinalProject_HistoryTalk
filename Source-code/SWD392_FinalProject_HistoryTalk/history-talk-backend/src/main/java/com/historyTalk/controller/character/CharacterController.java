package com.historyTalk.controller.character;

import com.historyTalk.dto.ApiResponse;
import com.historyTalk.dto.PaginatedResponse;
import com.historyTalk.dto.character.CharacterResponse;
import com.historyTalk.dto.character.CreateCharacterRequest;
import com.historyTalk.dto.character.UpdateCharacterRequest;
import com.historyTalk.service.character.CharacterService;
import com.historyTalk.utils.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/characters")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Characters", description = "API endpoints for managing historical characters")
public class CharacterController {

    private final CharacterService characterService;

    @GetMapping
    @Operation(summary = "Get all characters", description = "Retrieve paginated characters, optionally filtered by search keyword and era")
    public ResponseEntity<ApiResponse<PaginatedResponse<CharacterResponse>>> getAllCharacters(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false) String era,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int limit) {
        log.info("GET /v1/characters - search: {}, era: {}, page: {}, limit: {}", search, era, page, limit);
        PaginatedResponse<CharacterResponse> result = characterService.getAllCharacters(search, era, page, limit);
        return ResponseEntity.ok(ApiResponse.success(result, "Characters retrieved successfully"));
    }

    @GetMapping("/{characterId}")
    @Operation(summary = "Get character by ID")
    public ResponseEntity<ApiResponse<?>> getCharacterById(@PathVariable String characterId) {
        log.info("GET /v1/characters/{}", characterId);
        CharacterResponse result = characterService.getCharacterById(characterId);
        return ResponseEntity.ok(ApiResponse.success(result, "Character retrieved successfully"));
    }

    @GetMapping("/context/{contextId}")
    @Operation(summary = "Get characters by historical context")
    public ResponseEntity<ApiResponse<?>> getCharactersByContext(@PathVariable String contextId) {
        log.info("GET /v1/characters/context/{}", contextId);
        List<CharacterResponse> result = characterService.getCharactersByContext(contextId);
        return ResponseEntity.ok(ApiResponse.success(result, "Characters retrieved successfully"));
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a new character", description = "Create a new historical character (Staff/Admin only)")
    public ResponseEntity<ApiResponse<?>> createCharacter(
            @Valid @RequestBody CreateCharacterRequest request) {
        log.info("POST /v1/characters - name: {}", request.getName());
        String staffId = SecurityUtils.getStaffId();
        CharacterResponse result = characterService.createCharacter(request, staffId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, "Character created successfully"));
    }

    @PutMapping("/{characterId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update a character", description = "Update character details (creator or Admin only)")
    public ResponseEntity<ApiResponse<?>> updateCharacter(
            @PathVariable String characterId,
            @Valid @RequestBody UpdateCharacterRequest request) {
        log.info("PUT /v1/characters/{}", characterId);
        String staffId = SecurityUtils.getStaffId();
        String staffRole = SecurityUtils.getRoleName();
        CharacterResponse result = characterService.updateCharacter(characterId, request, staffId, staffRole);
        return ResponseEntity.ok(ApiResponse.success(result, "Character updated successfully"));
    }

    @DeleteMapping("/{characterId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete a character", description = "Delete a character (creator or Admin only)")
    public ResponseEntity<ApiResponse<?>> deleteCharacter(
            @PathVariable String characterId) {
        log.info("DELETE /v1/characters/{}", characterId);
        String staffId = SecurityUtils.getStaffId();
        String staffRole = SecurityUtils.getRoleName();
        characterService.deleteCharacter(characterId, staffId, staffRole);
        return ResponseEntity.ok(ApiResponse.success(null, "Character deleted successfully"));
    }
}
