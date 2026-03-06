package com.historyTalk.dto.user;

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
    @Size(max = 20, message = "Full name must not exceed 50 characters")
    private String full_name;
    @Pattern(regexp = "^(0[1-9]|[12][0-9]|3[01])\\/(0[1-9]|1[0-2])\\/(19\\d{2}|2000)$",
            message = "Date of birth must be in the format dd/MM/yyyy and a valid date")
    private String dob;
    @Pattern(
            regexp = "^(MALE|FEMALE)$",
            message = "Gender must be male, female or empty"
    )
    private String gender;
    @Size(min = 10, max = 10, message = "Phone number must be exactly 10 characters if provided")
    @Pattern(regexp = "^(?:\\+84|0)(?:3[2-9]|5[6|8|9]|7[0|6-9]|8[1-5|8|9]|9[0-9])\\d{7}$",
            message = "Invalid Vietnamese phone number format.Start with 0 followed by 9 digits.")
    private String phoneNumber;
    private String address;
    @Pattern(regexp = "^[a-z0-9](?:\\.?[a-z0-9]){5,29}@gmail\\.com$",
            message = "Invalid email! please enter with the correct format of google mail. ")
    private String email;
}
