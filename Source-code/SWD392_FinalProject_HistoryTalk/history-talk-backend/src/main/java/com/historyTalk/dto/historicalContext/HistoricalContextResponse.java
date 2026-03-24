package com.historyTalk.dto.historicalContext;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.historyTalk.entity.enums.EventCategory;
import com.historyTalk.entity.enums.EventEra;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricalContextResponse {
    
    @JsonProperty("contextId")
    private String contextId;
    
    @JsonProperty("name")
    private String name;
    
    @JsonProperty("description")
    private String description;
    
    @JsonProperty("era")
    private EventEra era;

    @JsonProperty("category")
    private EventCategory category;

    @JsonProperty("year")
    private Integer year;

    @JsonProperty("startYear")
    private Integer startYear;

    @JsonProperty("endYear")
    private Integer endYear;

    @JsonProperty("period")
    private String period;

    @JsonProperty("yearLabel")
    private String yearLabel;

    @JsonProperty("beforeTCN")
    private Boolean beforeTCN;

    @JsonProperty("location")
    private String location;

    @JsonProperty("imageUrl")
    private String imageUrl;

    @JsonProperty("videoUrl")
    private String videoUrl;

    @JsonProperty("isDraft")
    private Boolean isDraft;

    @JsonProperty("status")
    private String status;

    @JsonProperty("createdBy")
    private CreatedByInfo createdBy;
    
    @JsonProperty("createdDate")
    private LocalDateTime createdDate;
    
    @JsonProperty("updatedDate")
    private LocalDateTime updatedDate;

    @JsonProperty("deletedAt")
    private LocalDateTime deletedAt;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreatedByInfo {
        @JsonProperty("uid")
        private String uid;
        
        @JsonProperty("userName")
        private String userName;
    }
}
