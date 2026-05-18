package com.historytalk.application.historical_context.service;

import com.historytalk.presentation.historical_context.dto.CreateHistoricalContextRequest;
import com.historytalk.presentation.historical_context.dto.HistoricalContextResponse;
import com.historytalk.presentation.common.dto.PaginatedResponse;
import com.historytalk.presentation.historical_context.dto.UpdateHistoricalContextRequest;
import com.historytalk.dataaccess.shared.entity.enums.EventCategory;
import com.historytalk.dataaccess.shared.entity.enums.EventEra;
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
