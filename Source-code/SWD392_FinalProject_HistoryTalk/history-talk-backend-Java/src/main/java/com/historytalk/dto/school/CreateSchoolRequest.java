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

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSchoolRequest {

    @NotBlank(message = "Tên trường không được để trống")
    @Size(max = 255, message = "Tên trường không được vượt quá 255 ký tự")
    private String name;

    @Size(max = 50, message = "Mã trường không được vượt quá 50 ký tự")
    private String schoolCode;

    @Size(max = 500, message = "Địa chỉ không được vượt quá 500 ký tự")
    private String address;

    @NotBlank(message = "Email liên hệ không được để trống")
    @Email(message = "Email liên hệ không hợp lệ")
    private String contactEmail;

    @Size(max = 20, message = "Số điện thoại liên hệ không được vượt quá 20 ký tự")
    private String contactPhone;

    @NotNull(message = "Loại gói không được để trống")
    private EnterprisePackage packageType;

    @NotNull(message = "Bắt buộc đồng ý với chính sách nội dung lịch sử địa phương")
    private Boolean localHistoryPolicyAccepted;

    private LocalDate subscriptionStartDate;

    private LocalDate subscriptionEndDate;
}
