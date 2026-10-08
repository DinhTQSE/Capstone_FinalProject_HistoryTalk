package com.historytalk.service.school.student;

import com.historytalk.dto.school.StudentImportContext;
import com.historytalk.dto.school.StudentImportResultDto;
import org.springframework.web.multipart.MultipartFile;

public interface StudentImportProcessor {

    StudentImportResultDto processImport(MultipartFile file, StudentImportContext context);
}
