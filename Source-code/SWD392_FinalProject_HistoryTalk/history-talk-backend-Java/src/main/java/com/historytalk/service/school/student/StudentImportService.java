package com.historytalk.service.school.student;

import com.historytalk.dto.school.StudentImportContext;
import com.historytalk.dto.school.StudentImportResultDto;
import com.historytalk.entity.enums.ImportScope;
import com.historytalk.security.UserPrincipal;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface StudentImportService {

    StudentImportResultDto importStudents(ImportScope scope, MultipartFile file, StudentImportContext context);

    StudentImportResultDto importSchoolWide(MultipartFile file, Integer defaultInitialToken, UserPrincipal principal);

    StudentImportResultDto importToClassroom(UUID classroomId, MultipartFile file, Integer defaultInitialToken, UserPrincipal principal);
}
