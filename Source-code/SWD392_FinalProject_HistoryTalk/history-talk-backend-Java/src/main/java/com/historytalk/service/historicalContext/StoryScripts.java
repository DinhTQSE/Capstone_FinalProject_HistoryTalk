package com.historytalk.service.historicalContext;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.exception.InvalidRequestException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The storyScript ("kịch bản kể chuyện", visual novel) contract shared by global contexts and teachers' local contexts:
 * { version: 1, title, narratorId, status: draft|published, updatedAt, nodes[], edges[] }.
 * The inside of nodes/edges is the FE's business and is not checked.
 */
public final class StoryScripts {

    public static final int MAX_NODES = 300;
    public static final int MAX_BYTES = 500 * 1024;
    private static final Set<String> VALID_STATUSES = Set.of("draft", "published");

    private StoryScripts() {
    }

    /**
     * Validates a story script from a request and returns the JSON stored in the DB column (null when raw is null).
     *
     * @throws InvalidRequestException (400) when the object breaks the storyScript contract.
     */
    public static String serialize(Object raw, ObjectMapper objectMapper) {
        if (raw == null) {
            return null;
        }
        Map<String, Object> map;
        String json;
        try {
            Object value = raw instanceof String s ? objectMapper.readValue(s, Object.class) : raw;
            if (!(value instanceof Map<?, ?>)) {
                throw new InvalidRequestException("storyScript phải là một object");
            }
            json = objectMapper.writeValueAsString(value);
            map = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException ex) {
            throw new InvalidRequestException("storyScript không phải JSON hợp lệ");
        }

        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new InvalidRequestException("storyScript vượt quá dung lượng tối đa 500 KB");
        }
        Object version = map.get("version");
        if (!(version instanceof Number n) || n.doubleValue() != 1d) {
            throw new InvalidRequestException("storyScript.version phải là 1");
        }
        if (!(map.get("title") instanceof String)) {
            throw new InvalidRequestException("storyScript.title phải là chuỗi");
        }
        Object status = map.get("status");
        if (!(status instanceof String st) || !VALID_STATUSES.contains(st)) {
            throw new InvalidRequestException("storyScript.status phải là 'draft' hoặc 'published'");
        }
        Object narratorId = map.get("narratorId");
        if (narratorId != null && !(narratorId instanceof String)) {
            throw new InvalidRequestException("storyScript.narratorId phải là chuỗi hoặc null");
        }
        if (!(map.get("nodes") instanceof List<?> nodes)) {
            throw new InvalidRequestException("storyScript.nodes phải là mảng");
        }
        if (!(map.get("edges") instanceof List<?>)) {
            throw new InvalidRequestException("storyScript.edges phải là mảng");
        }
        if (nodes.size() > MAX_NODES) {
            throw new InvalidRequestException("storyScript tối đa " + MAX_NODES + " node");
        }
        return json;
    }

    /** True when the stored script JSON is a published one. */
    public static boolean isPublished(String json, ObjectMapper objectMapper) {
        return "published".equals(read(json, objectMapper).get("status"));
    }

    /** Ids of the characters a stored script shows: the narrator and every node's speaker. */
    public static Set<String> characterIds(String json, ObjectMapper objectMapper) {
        Map<String, Object> map = read(json, objectMapper);
        Set<String> ids = new LinkedHashSet<>();
        if (map.get("narratorId") instanceof String narrator && !narrator.isBlank()) {
            ids.add(narrator);
        }
        if (map.get("nodes") instanceof List<?> nodes) {
            for (Object node : nodes) {
                if (node instanceof Map<?, ?> n && n.get("data") instanceof Map<?, ?> data
                        && data.get("speakerId") instanceof String speaker && !speaker.isBlank()) {
                    ids.add(speaker);
                }
            }
        }
        return ids;
    }

    /** The stored script moved back to draft (content kept); the input when it cannot be read. */
    public static String asDraft(String json, ObjectMapper objectMapper) {
        Map<String, Object> map = new java.util.LinkedHashMap<>(read(json, objectMapper));
        if (map.isEmpty()) {
            return json;
        }
        map.put("status", "draft");
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            return json;
        }
    }

    /** The stored script's title, or "" when it has none. */
    public static String title(String json, ObjectMapper objectMapper) {
        return read(json, objectMapper).get("title") instanceof String t ? t : "";
    }

    private static Map<String, Object> read(String json, ObjectMapper objectMapper) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException ex) {
            return Map.of();
        }
    }

    /**
     * The stored script as returned to a caller: authors get drafts too, everyone else only a published script
     * (otherwise null).
     */
    public static Object visible(Object script, boolean canSeeDrafts) {
        if (script == null || canSeeDrafts) {
            return script;
        }
        return script instanceof Map<?, ?> m && "published".equals(m.get("status")) ? script : null;
    }
}
