package com.historytalk.service.school;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.school.ResetPasswordResponse;
import com.historytalk.dto.school.StudentResponse;
import com.historytalk.dto.school.UpdateStudentRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface StudentManagementService {

    PaginatedResponse<StudentResponse> getStudents(UUID schoolId, String search, Boolean active, Pageable pageable);

    StudentResponse getStudentById(UUID studentId, UUID schoolId);

    StudentResponse updateStudent(UUID studentId, UUID schoolId, UpdateStudentRequest request);

    StudentResponse updateStudentStatus(UUID studentId, UUID schoolId, boolean active);

    void deleteStudent(UUID studentId, UUID schoolId);

    ResetPasswordResponse resetStudentPassword(UUID studentId, UUID schoolId);
}
