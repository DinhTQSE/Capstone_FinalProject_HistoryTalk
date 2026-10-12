package com.historytalk.service.school;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.CreateTeacherRequest;
import com.historytalk.dto.school.ResetPasswordResponse;
import com.historytalk.dto.school.TeacherResponse;
import com.historytalk.dto.school.UpdateTeacherRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface TeacherManagementService {

    TeacherResponse createTeacher(CreateTeacherRequest request, UUID schoolId);

    PaginatedResponse<TeacherResponse> getTeachers(UUID schoolId, String search, Boolean active, Pageable pageable);

    TeacherResponse updateTeacher(UUID teacherId, UUID schoolId, UpdateTeacherRequest request);

    TeacherResponse updateTeacherStatus(UUID teacherId, UUID schoolId, boolean active);

    void deleteTeacher(UUID teacherId, UUID schoolId);

    ResetPasswordResponse resetTeacherPassword(UUID teacherId, UUID schoolId);
}
