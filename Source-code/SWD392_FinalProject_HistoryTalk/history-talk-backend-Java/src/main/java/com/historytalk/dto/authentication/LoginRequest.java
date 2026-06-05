package com.historytalk.dto.authentication;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Yêu cầu Email")
    @Email(message = "Định dạng email không hợp lệ")
    private String email;

    @NotBlank(message = "Yêu cầu mật khẩu")
    private String password;
}
