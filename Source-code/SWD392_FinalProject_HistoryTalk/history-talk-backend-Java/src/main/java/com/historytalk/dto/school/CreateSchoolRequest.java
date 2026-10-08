package com.historytalk.dto.school;

import com.historytalk.entity.enums.EnterprisePackage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSchoolRequest {

    @NotBlank(message = "School name must not be blank")
    @Size(max = 255, message = "School name must not exceed 255 characters")
    private String name;

    @NotBlank(message = "School code must not be blank")
    @Size(min = 2, max = 50, message = "School code must be between 2 and 50 characters")
    private String schoolCode;

    @Size(max = 500, message = "Address must not exceed 500 characters")
    private String address;

    @NotBlank(message = "Contact email must not be blank")
    @Email(message = "Contact email is invalid")
    private String contactEmail;

    @Size(max = 20, message = "Contact phone must not exceed 20 characters")
    private String contactPhone;

    @NotNull(message = "Package type must not be null")
    private EnterprisePackage packageType;

    @NotNull(message = "Local history policy acceptance is required")
    private Boolean localHistoryPolicyAccepted;
}
