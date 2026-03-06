package com.historyTalk.dto.authentication;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterStaffResponse {

    private String uid;
    private String staffId;
    private String userName;
    private String name;
    private String email;
    private String roleName;
    private String message;
}
