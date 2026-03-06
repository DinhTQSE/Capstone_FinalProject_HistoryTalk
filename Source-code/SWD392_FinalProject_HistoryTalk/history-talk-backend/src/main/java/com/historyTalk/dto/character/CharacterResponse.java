package com.historyTalk.dto.character;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.historyTalk.entity.enums.EventEra;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharacterResponse {

    @JsonProperty("characterId")
    private String characterId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("title")
    private String title;

    @JsonProperty("background")
    private String background;

    @JsonProperty("image")
    private String image;

    @JsonProperty("personality")
    private String personality;

    @JsonProperty("lifespan")
    private String lifespan;

    @JsonProperty("side")
    private String side;

    @JsonProperty("era")
    private EventEra era;

    @JsonProperty("events")
    private List<EventInfo> events;

    @JsonProperty("context")
    private ContextInfo context;

    @JsonProperty("createdBy")
    private StaffInfo createdBy;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class EventInfo {
        @JsonProperty("id")
        private String id;

        @JsonProperty("name")
        private String name;

        @JsonProperty("era")
        private EventEra era;

        @JsonProperty("year")
        private Integer year;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContextInfo {
        @JsonProperty("contextId")
        private String contextId;

        @JsonProperty("name")
        private String name;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StaffInfo {
        @JsonProperty("staffId")
        private String staffId;

        @JsonProperty("name")
        private String name;
    }
}
