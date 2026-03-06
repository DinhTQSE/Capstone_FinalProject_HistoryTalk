package com.historyTalk.dto.historicalContext;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateHistoricalContextRequest {
    
    @JsonProperty("name")
    @NotBlank(message = "Context name is required")
    @Size(min = 3, max = 100, message = "Context name must be between 3 and 100 characters")
    private String name;
    
    @JsonProperty("description")
    @NotBlank(message = "Context description is required")
    @Size(min = 10, max = 5000, message = "Context description must be between 10 and 5000 characters")
    private String description;
    
}
