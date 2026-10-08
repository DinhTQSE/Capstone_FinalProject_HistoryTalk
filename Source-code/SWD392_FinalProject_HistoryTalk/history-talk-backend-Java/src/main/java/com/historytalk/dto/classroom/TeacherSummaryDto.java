package com.historytalk.dto.classroom;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TeacherSummaryDto {

    private UUID uid;
    private String fullName;
    private String email;
    private String phoneNumber;
    private String subjectDepartment;
}
