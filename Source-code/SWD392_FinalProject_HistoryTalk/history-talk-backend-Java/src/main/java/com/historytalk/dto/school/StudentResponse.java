package com.historytalk.dto.school;

import com.historytalk.entity.enums.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentResponse {

    private UUID uid;
    private String studentCode;
    private String userName;
    private String fullName;
    private String email;
    private LocalDate dob;
    private Gender gender;
    private String phoneNumber;
    private Integer token;
    private Boolean active;
    private UUID schoolId;
    private String schoolName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
