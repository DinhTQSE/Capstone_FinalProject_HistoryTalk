package com.historytalk.dto.user;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserInformationRequest {
    @Size(max = 20, message = "Họ và tên không được vượt quá 50 ký tự")
    private String full_name;
    @Pattern(regexp = "^(0[1-9]|[12][0-9]|3[01])\\/(0[1-9]|1[0-2])\\/(19\\d{2}|2000)$",
            message = "Ngày sinh phải theo định dạng dd/MM/yyyy và là ngày hợp lệ")
    private String dob;
    @Pattern(
            regexp = "^(MALE|FEMALE)$",
            message = "Giới tính phải là male, female hoặc trống"
    )
    private String gender;
    @Size(min = 10, max = 10, message = "Số điện thoại phải có đúng 10 ký tự nếu được cung cấp")
    @Pattern(regexp = "^(?:\\+84|0)(?:3[2-9]|5[6|8|9]|7[0|6-9]|8[1-5|8|9]|9[0-9])\\d{7}$",
            message = "Định dạng số điện thoại Việt Nam không hợp lệ. Bắt đầu bằng 0 và theo sau là 9 chữ số.")
    private String phoneNumber;
    private String address;
    @Pattern(regexp = "^[a-z0-9](?:\\.?[a-z0-9]){5,29}@gmail\\.com$",
            message = "Email không hợp lệ! Vui lòng nhập đúng định dạng của google mail. ")
    private String email;
}
