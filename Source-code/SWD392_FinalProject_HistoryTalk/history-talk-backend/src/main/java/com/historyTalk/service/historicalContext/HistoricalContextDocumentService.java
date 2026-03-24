package com.historyTalk.service.historicalContext;

import com.historyTalk.dto.historicalContext.CreateHistoricalContextDocumentRequest;
import com.historyTalk.dto.historicalContext.HistoricalContextDocumentResponse;
import com.historyTalk.dto.historicalContext.UpdateHistoricalContextDocumentRequest;

import java.util.List;

public interface HistoricalContextDocumentService {

    List<HistoricalContextDocumentResponse> getAllDocuments(String userRole);

    List<HistoricalContextDocumentResponse> getDocumentsByContextId(String contextId, String userRole);

    List<HistoricalContextDocumentResponse> getDocumentsByStaffId(String userId, String userRole);

    List<HistoricalContextDocumentResponse> searchDocuments(String search, String userRole);

    HistoricalContextDocumentResponse getDocumentById(String docId, String userRole);

    HistoricalContextDocumentResponse createDocument(CreateHistoricalContextDocumentRequest request, String userId);

    HistoricalContextDocumentResponse updateDocument(String docId, UpdateHistoricalContextDocumentRequest request, String userId, String userRole);

    void deleteDocument(String docId, String userId, String userRole);
}
