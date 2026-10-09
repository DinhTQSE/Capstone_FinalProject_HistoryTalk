package com.historytalk.dto.school;

import com.historytalk.entity.enums.EnterprisePackage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchoolTokenQuotaResponse {

    private UUID schoolId;
    private String schoolName;
    private String schoolCode;
    private EnterprisePackage packageType;
    private Long totalSchoolTokenQuota;
    private Long unallocatedTokenQuota;
    private Long allocatedTokenQuota;
    private Double usagePercentage;
}
