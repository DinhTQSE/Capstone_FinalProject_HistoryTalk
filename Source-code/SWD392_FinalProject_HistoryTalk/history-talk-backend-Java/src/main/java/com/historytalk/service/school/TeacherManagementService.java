package com.historytalk.service.school;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.CreateTeacherRequest;
import com.historytalk.dto.school.TeacherResponse;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TeacherManagementService {

    TeacherResponse createTeacher(CreateTeacherRequest request, UUID schoolId);

    PaginatedResponse<TeacherResponse> getTeachers(UUID schoolId, String search, Pageable pageable);

    TeacherResponse updateTeacherStatus(UUID teacherId, UUID schoolId, boolean active);
}
