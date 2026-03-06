package com.historyTalk.dto.authentication;

import com.historyTalk.entity.UserType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterResponse {

    private String uid;
    private String userName;
    private String email;
    private UserType userType;
    private String message;
}
