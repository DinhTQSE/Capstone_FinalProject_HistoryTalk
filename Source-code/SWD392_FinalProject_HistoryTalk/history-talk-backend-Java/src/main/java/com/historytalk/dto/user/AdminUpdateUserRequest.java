package com.historytalk.dto.user;

import com.historytalk.entity.enums.Gender;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
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
public class AdminUpdateUserRequest {
    @Size(min = 3, max = 100, message = "Tên đăng nhập phải từ 3 đến 100 ký tự")
    private String userName;

    @Size(max = 150, message = "Họ và tên không được vượt quá 150 ký tự")
    private String fullName;

    @PastOrPresent(message = "Ngày sinh không thể ở tương lai")
    private LocalDate dob;

    private Gender gender;

    @Size(max = 20, message = "Số điện thoại không được vượt quá 20 ký tự")
    @Pattern(regexp = "^(?:\\+84|0)(?:3[2-9]|5[6-9]|7[0-9]|8[1-9]|9[0-9])\\d{7}$|^$",
            message = "Định dạng số điện thoại Việt Nam không hợp lệ")
    private String phoneNumber;

    @Size(max = 500, message = "Địa chỉ không được vượt quá 500 ký tự")
    private String address;

    @Size(max = 500, message = "URL Avatar không được vượt quá 500 ký tự")
    private String avatarUrl;
}
