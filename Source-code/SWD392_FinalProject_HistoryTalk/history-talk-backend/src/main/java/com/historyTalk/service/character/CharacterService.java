package com.historyTalk.service.character;

import com.historyTalk.dto.PaginatedResponse;
import com.historyTalk.dto.character.CharacterResponse;
import com.historyTalk.dto.character.CreateCharacterRequest;
import com.historyTalk.dto.character.UpdateCharacterRequest;
import com.historyTalk.entity.character.Character;
import com.historyTalk.entity.enums.EventEra;
import com.historyTalk.entity.historicalContext.HistoricalContext;
import com.historyTalk.entity.user.User;
import com.historyTalk.exception.InvalidRequestException;
import com.historyTalk.exception.ResourceNotFoundException;
import com.historyTalk.repository.CharacterRepository;
import com.historyTalk.repository.HistoricalContextRepository;
import com.historyTalk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CharacterService {

    private final CharacterRepository characterRepository;
    private final HistoricalContextRepository contextRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PaginatedResponse<CharacterResponse> getAllCharacters(String search, String eraStr, int page, int limit) {
        log.info("Fetching characters - search: {}, era: {}, page: {}, limit: {}", search, eraStr, page, limit);
        EventEra era = null;
        if (eraStr != null && !eraStr.isBlank()) {
            try {
                era = EventEra.valueOf(eraStr.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new InvalidRequestException("Invalid era value: " + eraStr);
            }
        }
        int pageSize = Math.min(limit, 20);
        Pageable pageable = PageRequest.of(Math.max(page - 1, 0), pageSize);
        Page<Character> result = characterRepository.findAllWithFilter(normalize(search), era, pageable);
        return PaginatedResponse.<CharacterResponse>builder()
                .content(result.getContent().stream().map(this::mapToResponse).collect(Collectors.toList()))
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .currentPage(page)
                .pageSize(pageSize)
                .hasNext(result.hasNext())
                .hasPrevious(result.hasPrevious())
                .build();
    }

    @Transactional(readOnly = true)
    public CharacterResponse getCharacterById(String characterId) {
        log.info("Fetching character with ID: {}", characterId);
        Character character = characterRepository.findById(UUID.fromString(characterId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Character not found with id: " + characterId));
        return mapToResponse(character);
    }

    @Transactional(readOnly = true)
    public List<CharacterResponse> getCharactersByContext(String contextId) {
        log.info("Fetching characters for context: {}", contextId);
        return characterRepository.findByContextIdOrderByNameAsc(UUID.fromString(contextId))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CharacterResponse createCharacter(CreateCharacterRequest request, String userId) {
        log.info("Creating character: {} by user: {}", request.getName(), userId);

        Set<HistoricalContext> contexts = resolveContexts(request);

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User not found with id: " + userId));

        Character character = Character.builder()
                .name(request.getName())
                .title(request.getTitle())
                .background(request.getBackground())
                .image(request.getImage())
                .personality(request.getPersonality())
                .lifespan(request.getLifespan())
                .side(request.getSide())
                .historicalContexts(contexts)
                .createdBy(user)
                .build();

        Character saved = characterRepository.save(character);
        log.info("Character created with ID: {}", saved.getCharacterId());
        return mapToResponse(saved);
    }

    @Transactional
    public CharacterResponse updateCharacter(String characterId, UpdateCharacterRequest request,
                                              String userId, String userRole) {
        log.info("Updating character: {} by user: {}", characterId, userId);

        Character character = characterRepository.findById(UUID.fromString(characterId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Character not found with id: " + characterId));

        if (!character.getCreatedBy().getUid().equals(UUID.fromString(userId))
                && !"ADMIN".equalsIgnoreCase(userRole)) {
            throw new InvalidRequestException(
                    "You do not have permission to update this character");
        }

        if (request.getName() != null && !request.getName().isBlank()) {
            character.setName(request.getName());
        }
        if (request.getTitle() != null) {
            character.setTitle(request.getTitle());
        }
        if (request.getBackground() != null && !request.getBackground().isBlank()) {
            character.setBackground(request.getBackground());
        }
        if (request.getImage() != null) {
            character.setImage(request.getImage());
        }
        if (request.getPersonality() != null) {
            character.setPersonality(request.getPersonality());
        }
        if (request.getLifespan() != null) {
            character.setLifespan(request.getLifespan());
        }
        if (request.getSide() != null) {
            character.setSide(request.getSide());
        }

        Character updated = characterRepository.save(character);
        log.info("Character updated: {}", characterId);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteCharacter(String characterId, String userId, String userRole) {
        log.info("Deleting character: {} by user: {}", characterId, userId);

        Character character = characterRepository.findById(UUID.fromString(characterId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Character not found with id: " + characterId));

        if (!character.getCreatedBy().getUid().equals(UUID.fromString(userId))
                && !"ADMIN".equalsIgnoreCase(userRole)) {
            throw new InvalidRequestException(
                    "You do not have permission to delete this character");
        }

        characterRepository.delete(character);
        log.info("Character deleted: {}", characterId);
    }

    @Transactional
    public void softDeleteCharacter(String characterId, String userId, String userRole) {
        log.info("Soft deleting character: {} by user: {}", characterId, userId);

        Character character = characterRepository.findById(UUID.fromString(characterId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Character not found with id: " + characterId));

        if (!character.getCreatedBy().getUid().equals(UUID.fromString(userId))
                && !"ADMIN".equalsIgnoreCase(userRole)) {
            throw new InvalidRequestException(
                    "You do not have permission to soft delete this character");
        }

        character.setDeletedAt(java.time.LocalDateTime.now());
        characterRepository.save(character);

        // Cascade soft-delete to documents
        if (character.getDocuments() != null) {
            character.getDocuments().forEach(doc -> doc.setDeletedAt(java.time.LocalDateTime.now()));
        }

        // Cascade soft-delete to chat sessions
        if (character.getChatSessions() != null) {
            character.getChatSessions().forEach(session -> {
                session.setDeletedAt(java.time.LocalDateTime.now());
                if (session.getMessages() != null) {
                    session.getMessages().forEach(msg -> msg.setDeletedAt(java.time.LocalDateTime.now()));
                }
            });
        }
    }

    private CharacterResponse mapToResponse(Character character) {
        HistoricalContext ctx = resolvePrimaryContext(character);
        List<CharacterResponse.EventInfo> events = ctx == null ? List.of() : ctx.getDocuments().stream()
                .map(doc -> CharacterResponse.EventInfo.builder()
                        .id(doc.getDocId().toString())
                        .name(doc.getTitle())
                .era(ctx.getEra())
                .year(ctx.getYear())
                        .build())
                .collect(Collectors.toList());

        List<CharacterResponse.ContextInfo> contexts = character.getHistoricalContexts().stream()
            .map(hc -> CharacterResponse.ContextInfo.builder()
                .contextId(hc.getContextId().toString())
                .name(hc.getName())
                .build())
            .toList();

        return CharacterResponse.builder()
                .characterId(character.getCharacterId().toString())
                .name(character.getName())
                .title(character.getTitle())
                .background(character.getBackground())
                .image(character.getImage())
                .personality(character.getPersonality())
                .lifespan(character.getLifespan())
                .side(character.getSide())
            .era(ctx != null ? ctx.getEra() : null)
                .events(events)
            .context(ctx == null ? null : CharacterResponse.ContextInfo.builder()
                .contextId(ctx.getContextId().toString())
                .name(ctx.getName())
                .build())
            .contexts(contexts)
                .createdBy(CharacterResponse.StaffInfo.builder()
                        .uid(character.getCreatedBy().getUid().toString())
                        .userName(character.getCreatedBy().getUserName())
                        .build())
                .build();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private Set<HistoricalContext> resolveContexts(CreateCharacterRequest request) {
        Set<String> ids = new HashSet<>();
        if (request.getContextId() != null && !request.getContextId().isBlank()) {
            ids.add(request.getContextId());
        }
        if (request.getContextIds() != null) {
            request.getContextIds().stream()
                    .filter(id -> id != null && !id.isBlank())
                    .forEach(ids::add);
        }

        if (ids.isEmpty()) {
            throw new InvalidRequestException("At least one contextId is required");
        }

        Set<HistoricalContext> contexts = new HashSet<>();
        for (String id : ids) {
            HistoricalContext ctx = contextRepository.findById(UUID.fromString(id))
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Historical Context not found with id: " + id));
            contexts.add(ctx);
        }
        return contexts;
    }

    private HistoricalContext resolvePrimaryContext(Character character) {
        return character.getHistoricalContexts()
                .stream()
                .sorted(Comparator.comparing(HistoricalContext::getContextId))
                .findFirst()
                .orElse(null);
    }
}
