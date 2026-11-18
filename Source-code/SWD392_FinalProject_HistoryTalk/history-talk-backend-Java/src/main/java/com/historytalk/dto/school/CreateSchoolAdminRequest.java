package com.historytalk.dto.school;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSchoolAdminRequest {

    @NotNull(message = "ID trường học không được để trống")
    private UUID schoolId;

    @NotBlank(message = "Họ tên không được để trống")
    @Size(min = 2, max = 150, message = "Họ tên phải từ 2 đến 150 ký tự")
    private String fullName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không hợp lệ")
    private String email;

    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
    private String phoneNumber;
}
