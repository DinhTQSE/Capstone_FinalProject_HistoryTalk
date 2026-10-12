package com.historytalk.service.school;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.CreateSchoolAdminRequest;
import com.historytalk.dto.school.CreateSchoolRequest;
import com.historytalk.dto.school.ResetPasswordResponse;
import com.historytalk.dto.school.SchoolAdminResponse;
import com.historytalk.dto.school.SchoolResponse;
import com.historytalk.dto.school.SchoolTokenQuotaResponse;
import com.historytalk.dto.school.UpdateSchoolAdminRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface SchoolManagementService {

    SchoolResponse createSchool(CreateSchoolRequest request);

    SchoolResponse getSchoolById(UUID schoolId);

    PaginatedResponse<SchoolResponse> getSchools(String search, Pageable pageable);

    SchoolAdminResponse createSchoolAdmin(CreateSchoolAdminRequest request);

    PaginatedResponse<SchoolAdminResponse> getSchoolAdmins(UUID schoolId, String search, Boolean active, Pageable pageable);

    SchoolAdminResponse updateSchoolAdmin(UUID adminId, UpdateSchoolAdminRequest request);

    SchoolAdminResponse updateSchoolAdminStatus(UUID adminId, boolean active);

    void deleteSchoolAdmin(UUID adminId);

    ResetPasswordResponse resetSchoolAdminPassword(UUID adminId);

    SchoolTokenQuotaResponse getSchoolTokenQuota(UUID schoolId);
}
