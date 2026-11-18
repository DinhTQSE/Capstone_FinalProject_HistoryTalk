package com.historytalk.dto.school;

import com.historytalk.entity.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateStudentRequest {

    @NotBlank(message = "Họ tên không được để trống")
    @Size(min = 2, max = 150, message = "Họ tên phải từ 2 đến 150 ký tự")
    private String fullName;

    @Past(message = "Ngày sinh phải là một ngày trong quá khứ")
    private LocalDate dob;

    private Gender gender;

    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
    private String phoneNumber;

    @Email(message = "Email không hợp lệ")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    private String email;
}
