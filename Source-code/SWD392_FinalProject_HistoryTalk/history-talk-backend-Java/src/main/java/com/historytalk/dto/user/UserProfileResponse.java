package com.historytalk.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.historytalk.entity.enums.Gender;
import com.historytalk.entity.enums.UserRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserProfileResponse {
    private String uid;
    private String userName;
    private String email;
    private UserRole role;
    private String fullName;
    private LocalDate dob;
    private Gender gender;
    private String phoneNumber;
    private String address;
    private String avatarUrl;
    private String tierId;
    private String tierTitle;
    private Integer token;
    private LocalDateTime lastActiveDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
