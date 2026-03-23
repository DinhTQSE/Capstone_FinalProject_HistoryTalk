package com.historyTalk.service.historicalContext;

import com.historyTalk.dto.historicalContext.CreateHistoricalContextDocumentRequest;
import com.historyTalk.dto.historicalContext.HistoricalContextDocumentResponse;
import com.historyTalk.dto.historicalContext.UpdateHistoricalContextDocumentRequest;

import java.util.List;

public interface HistoricalContextDocumentService {

    List<HistoricalContextDocumentResponse> getAllDocuments();

    List<HistoricalContextDocumentResponse> getDocumentsByContextId(String contextId);

    List<HistoricalContextDocumentResponse> getDocumentsByStaffId(String userId);

    List<HistoricalContextDocumentResponse> searchDocuments(String search);

    HistoricalContextDocumentResponse getDocumentById(String docId);

    HistoricalContextDocumentResponse createDocument(CreateHistoricalContextDocumentRequest request, String userId);

    HistoricalContextDocumentResponse updateDocument(String docId, UpdateHistoricalContextDocumentRequest request, String userId);

    void deleteDocument(String docId, String userId);
}
