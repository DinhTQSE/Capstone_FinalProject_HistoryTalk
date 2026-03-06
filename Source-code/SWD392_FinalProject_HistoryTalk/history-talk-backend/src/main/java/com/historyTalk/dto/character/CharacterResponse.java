package com.historyTalk.dto.character;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CharacterResponse {

    @JsonProperty("characterId")
    private String characterId;

    @JsonProperty("name")
    private String name;

    @JsonProperty("background")
    private String background;

    @JsonProperty("image")
    private String image;

    @JsonProperty("personality")
    private String personality;

    @JsonProperty("context")
    private ContextInfo context;

    @JsonProperty("createdBy")
    private StaffInfo createdBy;

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
