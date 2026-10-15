package com.historytalk.service.school.student;

import com.historytalk.dto.school.StudentImportContext;
import com.historytalk.dto.school.StudentImportResultDto;
import com.historytalk.entity.enums.ImportScope;
import org.springframework.web.multipart.MultipartFile;

public interface StudentImportProcessor {

    ImportScope getScope();

    StudentImportResultDto processImport(MultipartFile file, StudentImportContext context);
}

