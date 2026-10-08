package com.historytalk.service.school;

import com.historytalk.dto.school.CreateSchoolAdminRequest;
import com.historytalk.dto.school.CreateSchoolRequest;
import com.historytalk.dto.school.SchoolAdminResponse;
import com.historytalk.dto.school.SchoolResponse;

import java.util.UUID;

public interface SchoolManagementService {

    SchoolResponse createSchool(CreateSchoolRequest request);

    SchoolResponse getSchoolById(UUID schoolId);

    SchoolAdminResponse createSchoolAdmin(CreateSchoolAdminRequest request);
}
