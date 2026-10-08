package com.historytalk.dto.school;

import com.historytalk.entity.enums.EnterprisePackage;
import com.historytalk.entity.enums.SchoolStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchoolResponse {

    private UUID id;
    private String name;
    private String schoolCode;
    private String address;
    private String contactEmail;
    private String contactPhone;
    private EnterprisePackage packageType;
    private Integer totalSchoolTokenQuota;
    private Integer unallocatedTokenQuota;
    private Boolean localHistoryPolicyAccepted;
    private SchoolStatus status;
    private LocalDateTime createdAt;
}
