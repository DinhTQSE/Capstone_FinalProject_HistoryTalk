package com.historytalk.dto.school;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentImportRowDto {

    private int rowNumber;
    private String studentCode;
    private String fullName;
    private String email;
    private LocalDate dob;
    private String gender;
    private String phoneNumber;
    private String classCode;
    private String password;
    private Integer additionalToken;
}
