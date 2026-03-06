package com.historyTalk.service.character;

import com.historyTalk.dto.character.CharacterResponse;
import com.historyTalk.dto.character.CreateCharacterRequest;
import com.historyTalk.dto.character.UpdateCharacterRequest;
import com.historyTalk.entity.character.Character;
import com.historyTalk.entity.historicalContext.HistoricalContext;
import com.historyTalk.entity.staff.Staff;
import com.historyTalk.exception.InvalidRequestException;
import com.historyTalk.exception.ResourceNotFoundException;
import com.historyTalk.repository.CharacterRepository;
import com.historyTalk.repository.HistoricalContextRepository;
import com.historyTalk.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    public List<CharacterResponse> getAllCharacters(String search) {
        log.info("Fetching all characters with search: {}", search);
        return characterRepository.findAllWithSearch(normalize(search))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
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
                .background(request.getBackground())
                .image(request.getImage())
                .personality(request.getPersonality())
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
        if (request.getBackground() != null && !request.getBackground().isBlank()) {
            character.setBackground(request.getBackground());
        }
        if (request.getImage() != null) {
            character.setImage(request.getImage());
        }
        if (request.getPersonality() != null) {
            character.setPersonality(request.getPersonality());
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
        return CharacterResponse.builder()
                .characterId(character.getCharacterId().toString())
                .name(character.getName())
                .background(character.getBackground())
                .image(character.getImage())
                .personality(character.getPersonality())
                .context(CharacterResponse.ContextInfo.builder()
                        .contextId(character.getHistoricalContext().getContextId().toString())
                        .name(character.getHistoricalContext().getName())
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
