package com.historytalk.service.historicalContext;

import com.historytalk.dto.historicalContext.CreateHistoricalContextRequest;
import com.historytalk.dto.historicalContext.HistoricalContextResponse;
import com.historytalk.dto.PaginatedResponse;
import com.historytalk.dto.historicalContext.UpdateHistoricalContextRequest;
import com.historytalk.entity.enums.EventCategory;
import com.historytalk.entity.enums.EventEra;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface HistoricalContextService {

    PaginatedResponse<HistoricalContextResponse> getAllContexts(String search, EventEra era, EventCategory category, Pageable pageable, String role);

    List<HistoricalContextResponse> getAllContextsSimple(String search, String role);

    HistoricalContextResponse getContextById(String contextId, String role);

    HistoricalContextResponse createContext(CreateHistoricalContextRequest request, String userId);

    HistoricalContextResponse updateContext(String contextId, UpdateHistoricalContextRequest request, String userId, String userRole);

    void deleteContext(String contextId, String userId, String userRole);

    void softDeleteContext(String contextId, String userId, String userRole);

    List<HistoricalContextResponse> getDeletedContexts();

    void restoreContext(String contextId);

    void permanentDeleteContext(String contextId);
}