package com.historytalk.dto.authentication;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetPasswordRequest {

    @NotBlank(message = "Yêu cầu Reset token")
    private String token;

    @NotBlank(message = "Yêu cầu mật khẩu mới")
    @Size(min = 6, max = 100, message = "Mật khẩu mới phải từ 6 đến 100 ký tự")
    private String newPassword;

    @NotBlank(message = "Yêu cầu xác nhận mật khẩu")
    private String confirmPassword;
}
