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
public class ImportedStudentAccountDto {

    private UUID uid;
    private String studentCode;
    private String userName;
    private String fullName;
    private String email;
    private String classCode;
    private Integer allocatedToken;
    private String initialPassword; // Plaintext để in/phát cho học sinh
}
