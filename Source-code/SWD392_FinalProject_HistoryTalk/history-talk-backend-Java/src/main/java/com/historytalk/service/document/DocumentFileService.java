package com.historytalk.service.document;

import com.historytalk.dto.document.DocumentFileResponse;
import org.springframework.web.multipart.MultipartFile;

public interface DocumentFileService {
    DocumentFileResponse uploadPdfFile(String docId, MultipartFile file, String userId, String userRole);

    DownloadedDocumentFile downloadPdfFile(String docId, String userId, String userRole);

    DocumentPdfUrl createPdfUrl(String docId, String userId, String userRole);
}
