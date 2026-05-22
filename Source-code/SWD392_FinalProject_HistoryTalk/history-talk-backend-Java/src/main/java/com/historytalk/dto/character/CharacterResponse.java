package com.historytalk.dto.character;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.historytalk.entity.enums.EventEra;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
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

    @JsonProperty("imageUrl")
    private String imageUrl;

    @JsonProperty("personality")
    private String personality;

    @JsonProperty("bornDate")
    private java.time.LocalDate bornDate;

    @JsonProperty("deathDate")
    private java.time.LocalDate deathDate;

    @JsonProperty("isDraft")
    private Boolean isDraft;

    @JsonProperty("status")
    private String status;

    @JsonProperty("era")
    private EventEra era;

    @JsonProperty("events")
    private List<EventInfo> events;

    @JsonProperty("context")
    private ContextInfo context;

    @JsonProperty("contexts")
    private List<ContextInfo> contexts;

    @JsonProperty("createdBy")
    private StaffInfo createdBy;

    @JsonProperty("deletedAt")
    private LocalDateTime deletedAt;

    @JsonProperty("createdDate")
    private LocalDateTime createdDate;

    @JsonProperty("updatedDate")
    private LocalDateTime updatedDate;

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
        @JsonProperty("uid")
        private String uid;

        @JsonProperty("userName")
        private String userName;
    }
}
