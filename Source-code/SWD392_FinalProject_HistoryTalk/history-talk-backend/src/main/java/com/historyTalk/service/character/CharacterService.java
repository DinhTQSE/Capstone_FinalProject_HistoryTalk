package com.historyTalk.service.character;

import com.historyTalk.dto.PaginatedResponse;
import com.historyTalk.dto.character.CharacterResponse;
import com.historyTalk.dto.character.CreateCharacterRequest;
import com.historyTalk.dto.character.UpdateCharacterRequest;
import com.historyTalk.entity.character.Character;
import com.historyTalk.entity.enums.EventEra;
import com.historyTalk.entity.historicalContext.HistoricalContext;
import com.historyTalk.entity.staff.Staff;
import com.historyTalk.exception.InvalidRequestException;
import com.historyTalk.exception.ResourceNotFoundException;
import com.historyTalk.repository.CharacterRepository;
import com.historyTalk.repository.HistoricalContextRepository;
import com.historyTalk.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CharacterService {

    private final CharacterRepository characterRepository;
    private final HistoricalContextRepository contextRepository;
    private final StaffRepository staffRepository;

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
        return characterRepository.findByHistoricalContextContextIdOrderByNameAsc(UUID.fromString(contextId))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public CharacterResponse createCharacter(CreateCharacterRequest request, String staffId) {
        log.info("Creating character: {} by staff: {}", request.getName(), staffId);

        HistoricalContext context = contextRepository.findById(UUID.fromString(request.getContextId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Historical Context not found with id: " + request.getContextId()));

        Staff staff = staffRepository.findById(UUID.fromString(staffId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Staff not found with id: " + staffId));

        Character character = Character.builder()
                .name(request.getName())
                .title(request.getTitle())
                .background(request.getBackground())
                .image(request.getImage())
                .personality(request.getPersonality())
                .lifespan(request.getLifespan())
                .side(request.getSide())
                .historicalContext(context)
                .staff(staff)
                .build();

        Character saved = characterRepository.save(character);
        log.info("Character created with ID: {}", saved.getCharacterId());
        return mapToResponse(saved);
    }

    @Transactional
    public CharacterResponse updateCharacter(String characterId, UpdateCharacterRequest request,
                                              String staffId, String staffRole) {
        log.info("Updating character: {} by staff: {}", characterId, staffId);

        Character character = characterRepository.findById(UUID.fromString(characterId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Character not found with id: " + characterId));

        if (!character.getStaff().getStaffId().equals(UUID.fromString(staffId))
                && !"ADMIN".equalsIgnoreCase(staffRole)) {
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
    public void deleteCharacter(String characterId, String staffId, String staffRole) {
        log.info("Deleting character: {} by staff: {}", characterId, staffId);

        Character character = characterRepository.findById(UUID.fromString(characterId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Character not found with id: " + characterId));

        if (!character.getStaff().getStaffId().equals(UUID.fromString(staffId))
                && !"ADMIN".equalsIgnoreCase(staffRole)) {
            throw new InvalidRequestException(
                    "You do not have permission to delete this character");
        }

        characterRepository.delete(character);
        log.info("Character deleted: {}", characterId);
    }

    private CharacterResponse mapToResponse(Character character) {
        HistoricalContext ctx = character.getHistoricalContext();
        List<CharacterResponse.EventInfo> events = ctx.getDocuments().stream()
                .map(doc -> CharacterResponse.EventInfo.builder()
                        .id(doc.getDocId().toString())
                        .name(doc.getTitle())
                        .era(ctx.getEra())
                        .year(ctx.getYear())
                        .build())
                .collect(Collectors.toList());

        return CharacterResponse.builder()
                .characterId(character.getCharacterId().toString())
                .name(character.getName())
                .title(character.getTitle())
                .background(character.getBackground())
                .image(character.getImage())
                .personality(character.getPersonality())
                .lifespan(character.getLifespan())
                .side(character.getSide())
                .era(ctx.getEra())
                .events(events)
                .context(CharacterResponse.ContextInfo.builder()
                        .contextId(ctx.getContextId().toString())
                        .name(ctx.getName())
                        .build())
                .createdBy(CharacterResponse.StaffInfo.builder()
                        .staffId(character.getStaff().getStaffId().toString())
                        .name(character.getStaff().getName())
                        .build())
                .build();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }
}
