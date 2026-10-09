package com.historytalk.dto.school;

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
public class TeacherResponse {

    private UUID uid;
    private String userName;
    private String fullName;
    private String email;
    private String phoneNumber;
    private Boolean active;
    private String initialPassword; // Trả về khi tạo mới
    private LocalDateTime createdAt;
}
