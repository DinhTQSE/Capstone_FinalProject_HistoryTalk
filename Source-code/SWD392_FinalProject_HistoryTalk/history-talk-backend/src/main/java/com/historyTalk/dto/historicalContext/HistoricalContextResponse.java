package com.historyTalk.dto.historicalContext;

import com.fasterxml.jackson.annotation.JsonProperty;
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
