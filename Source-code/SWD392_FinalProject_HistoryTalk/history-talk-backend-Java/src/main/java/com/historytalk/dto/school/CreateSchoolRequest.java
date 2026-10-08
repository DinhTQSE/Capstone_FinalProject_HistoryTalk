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

    @NotBlank(message = "Tên trường không được để trống")
    @Size(max = 255, message = "Tên trường tối đa 255 ký tự")
    private String name;

    @NotBlank(message = "Mã trường không được để trống")
    @Size(min = 2, max = 50, message = "Mã trường từ 2 đến 50 ký tự")
    private String schoolCode;

    @Size(max = 500, message = "Địa chỉ tối đa 500 ký tự")
    private String address;

    @NotBlank(message = "Email liên hệ không được để trống")
    @Email(message = "Email liên hệ không đúng định dạng")
    private String contactEmail;

    @Size(max = 20, message = "Số điện thoại tối đa 20 ký tự")
    private String contactPhone;

    @NotNull(message = "Gói Enterprise không được để trống")
    private EnterprisePackage packageType;

    @NotNull(message = "Bắt buộc xác nhận điều khoản nội dung lịch sử địa phương")
    private Boolean localHistoryPolicyAccepted;
}
