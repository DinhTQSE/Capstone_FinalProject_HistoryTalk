package com.historytalk.dto.school;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AllocateStudentTokenResponse {

    private UUID schoolId;
    private Integer totalTokensAllocated;
    private Integer affectedStudentCount;
    private Integer tokenAmountPerStudent;
    private Integer remainingSchoolTokens;
    private String message;
}
