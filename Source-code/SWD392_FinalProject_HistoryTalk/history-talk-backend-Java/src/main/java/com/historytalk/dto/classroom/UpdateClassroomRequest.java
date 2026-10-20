package com.historytalk.dto.classroom;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateClassroomRequest {

    @Size(max = 100, message = "Tên lớp tối đa 100 ký tự")
    private String className;

    private Integer gradeLevel;

    private Integer academicYear;

    private UUID teacherId;

    @Size(max = 500, message = "Mô tả tối đa 500 ký tự")
    private String description;

    @Min(value = 0, message = "Hạn mức token hàng ngày không được âm")
    private Integer dailyTokenQuota;

    private String status;
}
