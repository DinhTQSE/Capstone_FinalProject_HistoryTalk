package com.historytalk.dto.school.quote;

import com.historytalk.entity.enums.B2bQuoteStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
public class UpdateB2bQuoteStatusRequest {

    @NotNull(message = "Trạng thái đích không được để trống")
    private B2bQuoteStatus status;

    @Min(value = 0, message = "Phần trăm giảm giá tùy chỉnh phải từ 0 đến 100")
    @Max(value = 100, message = "Phần trăm giảm giá tùy chỉnh phải từ 0 đến 100")
    private Integer customDiscountPercent;

    @Size(max = 2000, message = "Ghi chú của quản trị viên không được vượt quá 2.000 ký tự")
    private String adminNote;

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
