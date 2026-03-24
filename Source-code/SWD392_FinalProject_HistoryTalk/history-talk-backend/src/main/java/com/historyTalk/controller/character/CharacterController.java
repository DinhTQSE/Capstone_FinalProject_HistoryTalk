package com.historyTalk.controller.character;

import com.historyTalk.dto.ApiResponse;
import com.historyTalk.dto.PaginatedResponse;
import com.historyTalk.dto.character.CharacterResponse;
import com.historyTalk.dto.character.CreateCharacterRequest;
import com.historyTalk.dto.character.UpdateCharacterRequest;
import com.historyTalk.entity.enums.EventEra;
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
            @RequestParam(required = false) EventEra era,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "8") int limit) {
        log.info("GET /v1/characters - search: {}, era: {}, page: {}, limit: {}", search, era, page, limit);
        String role = SecurityUtils.getRoleName();
        PaginatedResponse<CharacterResponse> result = characterService.getAllCharacters(search, era != null ? era.toString() : null, page, limit, role);
        return ResponseEntity.ok(ApiResponse.success(result, "Characters retrieved successfully"));
    }

    @GetMapping("/{characterId}")
    @Operation(summary = "Get character by ID")
    public ResponseEntity<ApiResponse<?>> getCharacterById(@PathVariable String characterId) {
        log.info("GET /v1/characters/{}", characterId);
        String role = SecurityUtils.getRoleName();
        CharacterResponse result = characterService.getCharacterById(characterId, role);
        return ResponseEntity.ok(ApiResponse.success(result, "Character retrieved successfully"));
    }

    @GetMapping("/context/{contextId}")
    @Operation(summary = "Get characters by historical context")
    public ResponseEntity<ApiResponse<?>> getCharactersByContext(@PathVariable String contextId) {
        log.info("GET /v1/characters/context/{}", contextId);
        String role = SecurityUtils.getRoleName();
        List<CharacterResponse> result = characterService.getCharactersByContext(contextId, role);
        return ResponseEntity.ok(ApiResponse.success(result, "Characters retrieved successfully"));
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a new character", description = "Create a new historical character (Staff/Admin only)")
    public ResponseEntity<ApiResponse<?>> createCharacter(
            @Valid @RequestBody CreateCharacterRequest request) {
        log.info("POST /v1/characters - name: {}", request.getName());
        String staffId = SecurityUtils.getUserId();
        CharacterResponse result = characterService.createCharacter(request, staffId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(result, "Character created successfully"));
    }

    @PutMapping("/{characterId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update a character", description = "Update character details (Staff/Admin only)")
    public ResponseEntity<ApiResponse<?>> updateCharacter(
            @PathVariable String characterId,
            @Valid @RequestBody UpdateCharacterRequest request) {
        log.info("PUT /v1/characters/{}", characterId);
        String staffId = SecurityUtils.getUserId();
        String staffRole = SecurityUtils.getRoleName();
        CharacterResponse result = characterService.updateCharacter(characterId, request, staffId, staffRole);
        return ResponseEntity.ok(ApiResponse.success(result, "Character updated successfully"));
    }

    @DeleteMapping("/{characterId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete a character", description = "Delete a character (Staff/Admin only)")
    public ResponseEntity<ApiResponse<?>> deleteCharacter(
            @PathVariable String characterId) {
        log.info("DELETE /v1/characters/{}", characterId);
        String staffId = SecurityUtils.getUserId();
        String staffRole = SecurityUtils.getRoleName();
        characterService.deleteCharacter(characterId, staffId, staffRole);
        return ResponseEntity.ok(ApiResponse.success(null, "Character deleted successfully"));
    }

    @PatchMapping("/{characterId}/soft-delete")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Soft delete a character", description = "Soft delete a character (Staff/Admin only)")
    public ResponseEntity<ApiResponse<?>> softDeleteCharacter(
            @PathVariable String characterId) {
        log.info("PATCH /v1/characters/{}/soft-delete", characterId);
        String staffId = SecurityUtils.getUserId();
        String staffRole = SecurityUtils.getRoleName();
        characterService.softDeleteCharacter(characterId, staffId, staffRole);
        return ResponseEntity.ok(ApiResponse.success(null, "Character soft-deleted successfully"));
    }

    @PostMapping("/{characterId}/contexts/{contextId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Map context to character", description = "Create a character-context mapping (Staff/Admin only)")
    public ResponseEntity<ApiResponse<?>> addContextToCharacter(
            @PathVariable String characterId,
            @PathVariable String contextId) {
        log.info("POST /v1/characters/{}/contexts/{}", characterId, contextId);
        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        characterService.addContextToCharacter(characterId, contextId, userId, userRole);
        return ResponseEntity.ok(ApiResponse.success(null, "Context mapped to character successfully"));
    }

    @DeleteMapping("/{characterId}/contexts/{contextId}")
    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Unmap context from character", description = "Remove a character-context mapping (Staff/Admin only)")
    public ResponseEntity<ApiResponse<?>> removeContextFromCharacter(
            @PathVariable String characterId,
            @PathVariable String contextId) {
        log.info("DELETE /v1/characters/{}/contexts/{}", characterId, contextId);
        String userId = SecurityUtils.getUserId();
        String userRole = SecurityUtils.getRoleName();
        characterService.removeContextFromCharacter(characterId, contextId, userId, userRole);
        return ResponseEntity.ok(ApiResponse.success(null, "Context unmapped from character successfully"));
    }

    @GetMapping("/{characterId}/contexts")
    @Operation(summary = "Get contexts of a character", description = "Retrieve all mapped contexts of a character")
    public ResponseEntity<ApiResponse<List<CharacterResponse.ContextInfo>>> getContextsOfCharacter(
            @PathVariable String characterId) {
        log.info("GET /v1/characters/{}/contexts", characterId);
        String role = SecurityUtils.getRoleName();
        List<CharacterResponse.ContextInfo> contexts = characterService.getContextsOfCharacter(characterId, role);
        return ResponseEntity.ok(ApiResponse.success(contexts, "Character contexts retrieved successfully"));
    }
}
