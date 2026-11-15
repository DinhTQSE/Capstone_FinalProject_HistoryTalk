package com.historytalk.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.historytalk.entity.character.Character;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.CharacterRepository;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.UserRepository;
import com.historytalk.service.chat.AiServiceClient;
import com.historytalk.service.chat.AiServiceClient.AuthoringResponse;
import com.historytalk.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Drafts take seconds to minutes, so the database is only read in a short transaction before the AI call.
 */
@Service
@RequiredArgsConstructor
public class AiAuthoringServiceImpl implements AiAuthoringService {

    private static final Pattern JOB_ID = Pattern.compile("[0-9a-f]{32}");

    private final AiServiceClient aiServiceClient;
    private final HistoricalContextRepository contextRepository;
    private final CharacterRepository characterRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    @Override
    public AuthoringResponse generateStory(ObjectNode body) {
        return forward("POST", "/v1/ai/story/generate", withContextAndCharacters(body));
    }

    @Override
    public AuthoringResponse planBattleMap(ObjectNode body) {
        return forward("POST", "/v1/ai/battle-map/plan", withContextAndCharacters(body));
    }

    @Override
    public AuthoringResponse locateAnchors(ObjectNode body) {
        return forward("POST", "/v1/ai/battle-map/locate-anchors", body);
    }

    @Override
    public AuthoringResponse getJob(String jobId) {
        return aiServiceClient.forwardAuthoring("GET", "/v1/ai/jobs/" + checkedJobId(jobId), null);
    }

    @Override
    public AuthoringResponse cancelJob(String jobId) {
        return aiServiceClient.forwardAuthoring("DELETE", "/v1/ai/jobs/" + checkedJobId(jobId), null);
    }

    private AuthoringResponse forward(String method, String path, ObjectNode body) {
        return aiServiceClient.forwardAuthoring(method, path, body.toString());
    }

    private String checkedJobId(String jobId) {
        if (jobId == null || !JOB_ID.matcher(jobId).matches()) {
            throw new InvalidRequestException("jobId không hợp lệ.");
        }
        return jobId;
    }

    /**
     * The AI service writes from documents of the context and its characters. Sending the data from here lets
     * drafts of unpublished contexts work, which the AI service cannot read on its own.
     */
    private ObjectNode withContextAndCharacters(ObjectNode body) {
        UUID contextId = parseId(body.path("contextId").asText(null), "contextId");

        return transactionTemplate.execute(status -> {
            HistoricalContext context = contextRepository.findById(contextId)
                    .filter(c -> c.getDeletedAt() == null)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy bối cảnh."));
            checkSchoolAccess(context.getSchool());

            List<Character> characters = selectCharacters(
                    contextId, requestedCharacterIds(body), optionalId(body.path("narratorId").asText(null), "narratorId"));
            ArrayNode characterIds = body.putArray("characterIds");
            ArrayNode characterData = body.putArray("characterData");
            for (Character character : characters) {
                characterIds.add(character.getCharacterId().toString());
                characterData.add(objectMapper.valueToTree(AiServiceClient.buildCharacterPayload(character)));
            }
            body.set("contextData", objectMapper.valueToTree(contextData(context)));
            return body;
        });
    }

    private Set<UUID> requestedCharacterIds(ObjectNode body) {
        Set<UUID> ids = new LinkedHashSet<>();
        JsonNode requested = body.path("characterIds");
        if (requested.isArray()) {
            requested.forEach(id -> ids.add(parseId(id.asText(null), "characterIds")));
        }
        return ids;
    }

    private UUID optionalId(String value, String field) {
        return value == null || value.isBlank() ? null : parseId(value, field);
    }

    /**
     * By default everyone linked to the context may appear, otherwise only the requested characters.
     * The narrator always may.
     */
    private List<Character> selectCharacters(UUID contextId, Set<UUID> requested, UUID narratorId) {
        Set<UUID> wanted = new LinkedHashSet<>(requested);
        List<Character> characters = new ArrayList<>();
        if (requested.isEmpty()) {
            characters.addAll(characterRepository.findByContextIdOrderByNameAsc(contextId, true, false));
        }
        if (narratorId != null && characters.stream().noneMatch(c -> narratorId.equals(c.getCharacterId()))) {
            wanted.add(narratorId);
        }
        if (!wanted.isEmpty()) {
            List<Character> found = characterRepository.findAllById(wanted).stream()
                    .filter(c -> c.getDeletedAt() == null)
                    .toList();
            if (found.size() < wanted.size()) {
                throw new ResourceNotFoundException("Không tìm thấy nhân vật đã chọn.");
            }
            characters.addAll(found);
        }
        return characters;
    }

    /** A teacher drafts only for the local content of their own school; content admins draft for any. */
    private void checkSchoolAccess(School school) {
        if (school == null || "CONTENT_ADMIN".equalsIgnoreCase(SecurityUtils.getRoleName())) {
            return;
        }
        User user = userRepository.findById(UUID.fromString(SecurityUtils.getUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (user.getSchool() == null || !Objects.equals(user.getSchool().getId(), school.getId())) {
            throw new ResourceNotFoundException("Không tìm thấy bối cảnh.");
        }
    }

    private Map<String, Object> contextData(HistoricalContext context) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("contextId", context.getContextId().toString());
        data.put("name", context.getName());
        data.put("description", context.getDescription() != null ? context.getDescription() : "");
        data.put("era", context.getEra() != null ? context.getEra().name() : null);
        data.put("year", context.getYear());
        data.put("startYear", context.getStartYear());
        data.put("endYear", context.getEndYear());
        data.put("isBC", context.getIsBC());
        data.put("location", context.getLocation());
        return data;
    }

    private UUID parseId(String value, String field) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new InvalidRequestException(field + " không hợp lệ.");
        }
    }
}
