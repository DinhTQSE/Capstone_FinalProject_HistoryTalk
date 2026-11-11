package com.historytalk.service.character;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Lip-sync portrait settings of a character: an opaque JSON object owned by the FE portrait
 * editor (mode, mouthOpen, zoom, points, imageUrl...). The backend only checks that it is a JSON
 * object within {@link #MAX_SETTINGS_BYTES} and stores it as-is.
 */
public interface CharacterPortraitSettingsService {

    /** Maximum size of the raw request body: 8KB. */
    int MAX_SETTINGS_BYTES = 8 * 1024;

    /**
     * Returns the saved settings, or {@code null} when the character has none yet.
     * Uses the same visibility rules as {@code GET /characters/{id}}.
     *
     * @throws com.historytalk.exception.ResourceNotFoundException when the character is missing or hidden
     */
    JsonNode getPortraitSettings(String characterId, String role);

    /**
     * Validates {@code rawBody} (JSON object, at most {@link #MAX_SETTINGS_BYTES} bytes), checks the
     * caller may manage the character's media, saves the settings and returns them.
     *
     * @throws com.historytalk.exception.InvalidRequestException when the body is not a JSON object or too large
     * @throws com.historytalk.exception.UnauthorizedException when the caller may not edit this character
     * @throws com.historytalk.exception.ResourceNotFoundException when the character is missing or soft-deleted
     */
    JsonNode savePortraitSettings(String characterId, byte[] rawBody, String userId, String userRole);
}
