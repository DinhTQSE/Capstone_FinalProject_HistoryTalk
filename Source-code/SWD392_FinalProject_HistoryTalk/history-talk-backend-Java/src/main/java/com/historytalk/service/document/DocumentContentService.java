package com.historytalk.service.document;

import com.historytalk.dto.document.DocumentFileResponse;
import com.historytalk.dto.document.SaveDocumentRequest;

public interface DocumentContentService {
    DocumentFileResponse saveDocumentContent(SaveDocumentRequest request, String userId, String userRole);
}
