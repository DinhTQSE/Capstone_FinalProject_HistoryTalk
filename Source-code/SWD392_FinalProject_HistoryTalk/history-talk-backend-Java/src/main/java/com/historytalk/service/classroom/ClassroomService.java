package com.historytalk.service.classroom;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.classroom.CreateClassroomRequest;
import com.historytalk.dto.classroom.ClassroomResponse;
import com.historytalk.dto.classroom.UpdateClassroomRequest;
import com.historytalk.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface ClassroomService {

    ClassroomResponse createClassroom(CreateClassroomRequest request, UserPrincipal principal);

    ClassroomResponse updateClassroom(UUID id, UpdateClassroomRequest request, UserPrincipal principal);

    ClassroomResponse getClassroomById(UUID id, UserPrincipal principal);

    PaginatedResponse<ClassroomResponse> getSchoolClassrooms(
            String search, Integer gradeLevel, Integer academicYear, Pageable pageable, UserPrincipal principal);

    PaginatedResponse<ClassroomResponse> getTeacherClassrooms(
            Integer academicYear, Pageable pageable, UserPrincipal principal);

    List<ClassroomResponse> getStudentClassrooms(UserPrincipal principal);

    void deleteClassroom(UUID id, UserPrincipal principal);
}
