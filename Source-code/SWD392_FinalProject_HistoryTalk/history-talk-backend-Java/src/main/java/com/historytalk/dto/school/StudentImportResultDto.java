package com.historytalk.dto.school;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentImportResultDto {

    private int totalRows;
    private int successCount;
    private int failureCount;
    private int totalTokensAllocated;
    private int remainingSchoolTokens;

    @Builder.Default
    private List<ImportErrorDetail> errors = new ArrayList<>();

    @Builder.Default
    private List<ImportedStudentAccountDto> successfulAccounts = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportErrorDetail {
        private int rowNumber;
        private String studentCode;
        private String field;
        private String message;
    }
}
