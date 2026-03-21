package com.historyTalk.dto.user;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserInformationResponse {
    private UUID userId;
    private String full_name;
    private String dob;
    private String gender;
    private String phoneNumber;
    private String address;
    private String email;
    private String updatedBy;
    private String avatarUrl;
}
