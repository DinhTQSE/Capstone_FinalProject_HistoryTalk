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

    @JsonProperty("createdBy")
    private CreatedByInfo createdBy;
    
    @JsonProperty("createdDate")
    private LocalDateTime createdDate;
    
    @JsonProperty("updatedDate")
    private LocalDateTime updatedDate;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CreatedByInfo {
        @JsonProperty("staffId")
        private String staffId;
        
        @JsonProperty("name")
        private String name;
    }
}
