package com.historytalk.dto.school.quote;

import com.historytalk.entity.enums.EnterprisePackage;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalculateB2bQuoteRequest {

    private EnterprisePackage selectedPackage;

    @NotNull(message = "Số lượng học sinh không được để trống")
    @Min(value = 1, message = "Số lượng học sinh tối thiểu là 1")
    @Max(value = 50000, message = "Số lượng học sinh không được vượt quá 50.000")
    private Integer numberOfStudents;

    @NotNull(message = "Số lượng giáo viên không được để trống")
    @Min(value = 1, message = "Số lượng giáo viên tối thiểu là 1")
    @Max(value = 5000, message = "Số lượng giáo viên không được vượt quá 5.000")
    private Integer numberOfTeachers;

    private String promoCode;

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

    private LocalDate subscriptionStartDate;

    private LocalDate subscriptionEndDate;
}
