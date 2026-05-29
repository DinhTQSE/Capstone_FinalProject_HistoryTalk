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
    @Size(min = 3, max = 100, message = "Username must be between 3 and 100 characters")
    private String userName;

    @Size(max = 150, message = "Full name must not exceed 150 characters")
    private String fullName;

    @PastOrPresent(message = "Date of birth cannot be in the future")
    private LocalDate dob;

    private Gender gender;

    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    @Pattern(regexp = "^(?:\\+84|0)(?:3[2-9]|5[6-9]|7[0-9]|8[1-9]|9[0-9])\\d{7}$|^$",
            message = "Invalid Vietnamese phone number format")
    private String phoneNumber;

    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;

    @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
    private String avatarUrl;
}
