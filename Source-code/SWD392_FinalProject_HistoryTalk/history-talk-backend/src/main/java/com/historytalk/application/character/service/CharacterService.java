package com.historytalk.application.character.service;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.presentation.character.dto.CharacterResponse;
import com.historytalk.presentation.character.dto.CreateCharacterRequest;
import com.historytalk.presentation.character.dto.UpdateCharacterRequest;

import java.util.List;

public interface CharacterService {

    PaginatedResponse<CharacterResponse> getAllCharacters(String search, String eraStr, int page, int limit, String role);

    CharacterResponse getCharacterById(String characterId, String role);

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
