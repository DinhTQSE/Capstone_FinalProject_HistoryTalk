package com.historytalk.dto.school;

import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSchoolTokenPolicyRequest {

    @Min(value = 100, message = "Hạn mức hằng ngày mặc định của học sinh tối thiểu là 100 token")
    private Integer defaultDailyStudentQuota;

    @Min(value = 500, message = "Hạn mức hằng ngày mặc định của giáo viên tối thiểu là 500 token")
    private Integer defaultDailyTeacherQuota;

    private UUID classroomId;

    private Integer gradeLevel;

    @Min(value = 100, message = "Hạn mức hằng ngày của lớp học tối thiểu là 100 token")
    private Integer classroomDailyQuota;
}
