package com.historytalk.service.classroom;

import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.classroom.BatchEnrollResultResponse;
import com.historytalk.dto.classroom.BatchEnrollStudentRequest;
import com.historytalk.dto.classroom.ClassroomStudentResponse;
import com.historytalk.dto.classroom.TransferStudentRequest;
import com.historytalk.dto.school.StudentImportResultDto;
import com.historytalk.dto.school.StudentResponse;
import com.historytalk.security.UserPrincipal;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ClassroomStudentService {

    BatchEnrollResultResponse enrollStudentsBatch(UUID classroomId, BatchEnrollStudentRequest request, UserPrincipal principal);

    void removeStudentFromClass(UUID classroomId, UUID studentId, UserPrincipal principal);

    PaginatedResponse<ClassroomStudentResponse> getClassroomStudents(UUID classroomId, String search, Pageable pageable, UserPrincipal principal);

    PaginatedResponse<StudentResponse> getAvailableStudents(UUID classroomId, String search, Pageable pageable, UserPrincipal principal);

    BatchEnrollResultResponse transferStudents(UUID fromClassroomId, TransferStudentRequest request, UserPrincipal principal);

    StudentImportResultDto importClassroomStudents(UUID classroomId, MultipartFile file, Integer defaultInitialToken, UserPrincipal principal);
}
