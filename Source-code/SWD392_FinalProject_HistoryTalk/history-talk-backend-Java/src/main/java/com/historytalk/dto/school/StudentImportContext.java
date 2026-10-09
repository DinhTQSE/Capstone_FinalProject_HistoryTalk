package com.historytalk.dto.school;

import com.historytalk.entity.enums.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudentImportContext {

    private UUID schoolId;
    private String schoolCode;
    private UUID operatorId;
    private UserRole operatorRole;
    private String classCode; // Mã lớp học nếu import theo lớp (Case 1), null nếu là toàn trường (Case 2)
    private Integer defaultInitialToken;
}
