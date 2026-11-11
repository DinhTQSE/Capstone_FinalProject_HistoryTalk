package com.historytalk.service.character;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.character.CharacterResponse;
import com.historytalk.dto.character.CreateCharacterRequest;
import com.historytalk.dto.character.UpdateCharacterRequest;
import com.historytalk.entity.character.Character;

import java.util.List;

public interface CharacterService {

    PaginatedResponse<CharacterResponse> getAllCharacters(String search, String eraStr, int page, int limit, String role);

    CharacterResponse getCharacterById(String characterId, String role);

    /**
     * Loads a character applying the same visibility rules as {@link #getCharacterById}:
     * unpublished, soft-deleted or other-school local characters are hidden from non-staff callers.
     *
     * @throws com.historytalk.exception.ResourceNotFoundException when missing or hidden from the caller
     */
    Character findVisibleCharacter(String characterId, String role);

    List<CharacterResponse> getCharactersByContext(String contextId, String role);

    CharacterResponse createCharacter(CreateCharacterRequest request, String userId);

    CharacterResponse updateCharacter(String characterId, UpdateCharacterRequest request, String userId, String userRole);

    void deleteCharacter(String characterId, String userId, String userRole);

    void softDeleteCharacter(String characterId, String userId, String userRole);

    List<CharacterResponse> getDeletedCharacters();

    void restoreCharacter(String characterId);

    void permanentDeleteCharacter(String characterId);

    void addContextToCharacter(String characterId, String contextId, String userId, String userRole);

    void removeContextFromCharacter(String characterId, String contextId, String userId, String userRole);

    List<CharacterResponse.ContextInfo> getContextsOfCharacter(String characterId, String role);
}
