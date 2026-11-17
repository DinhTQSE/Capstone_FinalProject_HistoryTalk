package com.historytalk.dto.school.quote;

import com.historytalk.entity.enums.EnterprisePackage;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class SubmitB2bQuoteRequest {

    @NotBlank(message = "Tên không được để trống")
    @Size(max = 100, message = "Tên không được vượt quá 100 ký tự")
    private String firstName;

    @NotBlank(message = "Họ không được để trống")
    @Size(max = 100, message = "Họ không được vượt quá 100 ký tự")
    private String lastName;

    @NotBlank(message = "Email liên hệ không được để trống")
    @Email(message = "Email liên hệ không hợp lệ")
    @Size(max = 150, message = "Email liên hệ không được vượt quá 150 ký tự")
    private String contactEmail;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Size(max = 30, message = "Số điện thoại không được vượt quá 30 ký tự")
    private String contactPhone;

    @NotBlank(message = "Chức vụ không được để trống")
    @Size(max = 150, message = "Chức vụ không được vượt quá 150 ký tự")
    private String jobTitle;

    @NotBlank(message = "Tên trường không được để trống")
    @Size(max = 255, message = "Tên trường không được vượt quá 255 ký tự")
    private String schoolName;

    @Size(max = 50, message = "Mã trường không được vượt quá 50 ký tự")
    private String schoolCode;

    @Size(max = 500, message = "Địa chỉ trường không được vượt quá 500 ký tự")
    private String schoolAddress;

    @Builder.Default
    private EnterprisePackage selectedPackage = EnterprisePackage.ENTERPRISE_CUSTOM;

    @NotNull(message = "Số lượng học sinh không được để trống")
    @Min(value = 1, message = "Số lượng học sinh tối thiểu là 1")
    @Max(value = 50000, message = "Số lượng học sinh không được vượt quá 50.000")
    private Integer numberOfStudents;

    @NotNull(message = "Số lượng giáo viên không được để trống")
    @Min(value = 1, message = "Số lượng giáo viên tối thiểu là 1")
    @Max(value = 5000, message = "Số lượng giáo viên không được vượt quá 5.000")
    private Integer numberOfTeachers;

    @Min(value = 1, message = "Số tuần học tối thiểu là 1")
    @Max(value = 52, message = "Số tuần học không được vượt quá 52")
    private Integer academicWeeks;

    @Min(value = 100, message = "Hạn mức token hằng ngày của học sinh tối thiểu là 100")
    @Max(value = 100000, message = "Hạn mức token hằng ngày của học sinh không được vượt quá 100.000")
    private Integer studentDailyTokenLimit;

    @Min(value = 100, message = "Hạn mức token hằng ngày của giáo viên tối thiểu là 100")
    @Max(value = 100000, message = "Hạn mức token hằng ngày của giáo viên không được vượt quá 100.000")
    private Integer teacherDailyTokenLimit;

    @Min(value = 1, message = "Số ngày học mỗi tuần của học sinh phải từ 1 đến 7")
    @Max(value = 7, message = "Số ngày học mỗi tuần của học sinh phải từ 1 đến 7")
    private Integer studentDaysPerWeek;

    @Min(value = 1, message = "Số ngày dạy mỗi tuần của giáo viên phải từ 1 đến 7")
    @Max(value = 7, message = "Số ngày dạy mỗi tuần của giáo viên phải từ 1 đến 7")
    private Integer teacherDaysPerWeek;

    @Size(max = 2000, message = "Lời nhắn không được vượt quá 2.000 ký tự")
    private String message;

    @Size(max = 50, message = "Mã khuyến mãi không được vượt quá 50 ký tự")
    private String promoCode;

    private LocalDate subscriptionStartDate;

    private LocalDate subscriptionEndDate;
}
