package com.historytalk.dto.school;

import com.historytalk.entity.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchoolAdminResponse {

    private UUID uid;
    private String userName;
    private String email;
    private String fullName;
    private String phoneNumber;
    private UserRole role;
    private UUID schoolId;
    private String schoolName;
    private String initialPassword; // Plaintext trả về 1 lần duy nhất cho System Admin
    private LocalDateTime createdAt;
}
