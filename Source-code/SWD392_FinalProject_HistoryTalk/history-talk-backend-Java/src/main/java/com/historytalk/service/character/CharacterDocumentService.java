package com.historytalk.service.character;

import com.historytalk.dto.character.CreateCharacterDocumentRequest;
import com.historytalk.dto.character.CharacterDocumentResponse;
import com.historytalk.dto.character.UpdateCharacterDocumentRequest;

import java.util.List;

public interface CharacterDocumentService {

    List<CharacterDocumentResponse> getAllDocuments(String userRole);

    List<CharacterDocumentResponse> getDocumentsByCharacterId(String characterId, String userRole);

    List<CharacterDocumentResponse> getDocumentsByStaffId(String userId, String userRole);

    List<CharacterDocumentResponse> searchDocuments(String search, String userRole);

    CharacterDocumentResponse getDocumentById(String docId, String userRole);

    CharacterDocumentResponse createDocument(CreateCharacterDocumentRequest request, String userId);

    CharacterDocumentResponse updateDocument(String docId, UpdateCharacterDocumentRequest request, String userId, String userRole);

    void deleteDocument(String docId, String userId, String userRole);
}
