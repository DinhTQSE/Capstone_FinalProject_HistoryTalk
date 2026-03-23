package com.historyTalk.service.character;

import com.historyTalk.dto.PaginatedResponse;
import com.historyTalk.dto.character.CharacterResponse;
import com.historyTalk.dto.character.CreateCharacterRequest;
import com.historyTalk.dto.character.UpdateCharacterRequest;

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
}
