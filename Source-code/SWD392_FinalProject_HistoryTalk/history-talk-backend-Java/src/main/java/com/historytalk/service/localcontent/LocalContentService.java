package com.historytalk.service.localcontent;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.localcontent.*;
import com.historytalk.security.UserPrincipal;

/**
 * Business logic for local (school-owned) history content.
 * Covers both Context and Character local content.
 */
public interface LocalContentService {

    // ── Teacher: Context ─────────────────────────────────────────────────────

    LocalContentResponse createLocalContext(CreateLocalContextRequest request, UserPrincipal principal);

    LocalContentResponse updateLocalContext(String contextId, UpdateLocalContextRequest request, UserPrincipal principal);

    void deleteLocalContext(String contextId, UserPrincipal principal);

    LocalContentResponse getLocalContextById(String contextId, UserPrincipal principal);

    PaginatedResponse<LocalContentResponse> getLocalContexts(String search, int page, int size, UserPrincipal principal);

    // ── Teacher: Character ───────────────────────────────────────────────────

    LocalContentResponse createLocalCharacter(CreateLocalCharacterRequest request, UserPrincipal principal);

    LocalContentResponse updateLocalCharacter(String characterId, UpdateLocalCharacterRequest request, UserPrincipal principal);

    void deleteLocalCharacter(String characterId, UserPrincipal principal);

    LocalContentResponse getLocalCharacterById(String characterId, UserPrincipal principal);

    PaginatedResponse<LocalContentResponse> getLocalCharacters(String search, int page, int size, UserPrincipal principal);

    // ── School Admin: review panel ───────────────────────────────────────────

    PaginatedResponse<LocalContentResponse> getSchoolLocalContexts(String search, String scope, int page, int size, UserPrincipal principal);

    PaginatedResponse<LocalContentResponse> getSchoolLocalCharacters(String search, String scope, int page, int size, UserPrincipal principal);

    LocalContentResponse approveLocalContext(String contextId, UpdateLocalContentStatusRequest request, UserPrincipal principal);

    LocalContentResponse approveLocalCharacter(String characterId, UpdateLocalContentStatusRequest request, UserPrincipal principal);
}
