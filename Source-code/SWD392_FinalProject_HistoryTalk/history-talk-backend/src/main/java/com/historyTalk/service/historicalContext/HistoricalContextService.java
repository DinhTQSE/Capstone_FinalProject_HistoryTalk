package com.historyTalk.service.historicalContext;

import com.historyTalk.dto.historicalContext.CreateHistoricalContextRequest;
import com.historyTalk.dto.historicalContext.HistoricalContextResponse;
import com.historyTalk.dto.PaginatedResponse;
import com.historyTalk.dto.historicalContext.UpdateHistoricalContextRequest;
import com.historyTalk.entity.enums.EventCategory;
import com.historyTalk.entity.enums.EventEra;
import com.historyTalk.entity.historicalContext.HistoricalContext;
import com.historyTalk.entity.user.User;
import com.historyTalk.exception.InvalidRequestException;
import com.historyTalk.exception.ResourceNotFoundException;
import com.historyTalk.repository.HistoricalContextRepository;
import com.historyTalk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HistoricalContextService {

        private final HistoricalContextRepository contextRepository;
        private final UserRepository userRepository;
    
    /**
     * Get all historical contexts with pagination and search
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<HistoricalContextResponse> getAllContexts(
            String search, EventEra era, EventCategory category, Pageable pageable) {
        
        log.info("Fetching historical contexts with search: {}, era: {}, category: {}", search, era, category);
        
        Page<HistoricalContext> page = contextRepository
                .findAllWithSearch(normalize(search), era, category, pageable);
        
        return mapPageToPaginatedResponse(page);
    }

    /**
     * Get all historical contexts as simple list (no pagination)
     */
    @Transactional(readOnly = true)
    public List<HistoricalContextResponse> getAllContextsSimple(String search) {
        log.info("Fetching all historical contexts with search: {}", search);
        
        return contextRepository
                .findAllSimple(normalize(search))
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get a specific historical context by ID
     */
    @Transactional(readOnly = true)
    public HistoricalContextResponse getContextById(String contextId) {
        log.info("Fetching historical context with ID: {}", contextId);
        
        HistoricalContext context = contextRepository.findById(UUID.fromString(contextId))
                .orElseThrow(() -> new ResourceNotFoundException("Historical context not found with ID: "+contextId));
        
        return mapToResponse(context);
    }
    
    /**
     * Create a new historical context (Staff only)
     */
    @Transactional
    public HistoricalContextResponse createContext(
            CreateHistoricalContextRequest request, String userId) {
        
        log.info("Creating historical context: {} by user: {}", request.getName(), userId);
        
        // Check for duplicate name
        if (contextRepository.findByNameIgnoreCase(request.getName()).isPresent()) {
            throw new InvalidRequestException("Historical context already existed");
        }

        User user = userRepository.findById(UUID.fromString(userId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        HistoricalContext context = HistoricalContext.builder()
                .name(request.getName())
                .description(request.getDescription())
                .era(request.getEra())
                .category(request.getCategory())
                .year(request.getYear())
                .startYear(request.getStartYear())
                .endYear(request.getEndYear())
                .beforeTCN(request.getBeforeTCN() != null ? request.getBeforeTCN() : false)
                .location(request.getLocation())
                .imageUrl(request.getImageUrl())
                .createdBy(user)
                .build();

        HistoricalContext savedContext = contextRepository.save(context);
        log.info("Historical context created successfully with ID: {}", savedContext.getContextId());
        
        return mapToResponse(savedContext);
    }
    
    /**
     * Update a historical context (Only creator or admin can update)
     */
    @Transactional
    public HistoricalContextResponse updateContext(
            String contextId, UpdateHistoricalContextRequest request, 
            String userId, String userRole) {
        
        log.info("Updating historical context with ID: {}", contextId);
        
        HistoricalContext context = contextRepository.findById(UUID.fromString(contextId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Historical context not found with ID: "+contextId
                ));
        
        // Check if user has permission to update (creator or admin)
        if (!context.getCreatedBy().getUid().equals(UUID.fromString(userId)) && !"ADMIN".equalsIgnoreCase(userRole)) {
            throw new InvalidRequestException(
                    "You do not have permission to update this historical context"
            );
        }
        
        // Check for duplicate name (if name is being changed)
        if (request.getName() != null && 
            !request.getName().equalsIgnoreCase(context.getName()) &&
            contextRepository.existsByNameIgnoreCaseAndContextIdNot(request.getName(), UUID.fromString(contextId))) {
            throw new InvalidRequestException("Name already existed"+request.getName());
        }
        
        // Update fields
        if (request.getName() != null) {
            context.setName(request.getName());
        }
        if (request.getDescription() != null) {
            context.setDescription(request.getDescription());
        }
        if (request.getEra() != null) {
            context.setEra(request.getEra());
        }
        if (request.getCategory() != null) {
            context.setCategory(request.getCategory());
        }
        if (request.getYear() != null) {
            context.setYear(request.getYear());
        }
        if (request.getStartYear() != null) {
            context.setStartYear(request.getStartYear());
        }
        if (request.getEndYear() != null) {
            context.setEndYear(request.getEndYear());
        }
        if (request.getBeforeTCN() != null) {
            context.setBeforeTCN(request.getBeforeTCN());
        }
        if (request.getLocation() != null) {
            context.setLocation(request.getLocation());
        }
        if (request.getImageUrl() != null) {
            context.setImageUrl(request.getImageUrl());
        }
        HistoricalContext updatedContext = contextRepository.save(context);
        log.info("Historical context updated successfully with ID: {}", contextId);
        
        return mapToResponse(updatedContext);
    }
    
        /**
         * Delete a historical context (only creator or admin)
         */
    @Transactional
    public void deleteContext(String contextId, String userId, String userRole) {
        log.info("Deleting historical context with ID: {}", contextId);
        
        HistoricalContext context = contextRepository.findById(UUID.fromString(contextId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Not found with ID: "+contextId
                ));
        
        // Check if user has permission to delete (creator or admin)
        if (!context.getCreatedBy().getUid().equals(UUID.fromString(userId)) && !"ADMIN".equalsIgnoreCase(userRole)) {
            throw new InvalidRequestException(
                    "You do not have permission to delete this historical context"
            );
        }
        
        contextRepository.delete(context);
        log.info("Historical context deleted successfully with ID: {}", contextId);
    }
    
    /**
     * Helper method to map entity to response DTO
     */
    private HistoricalContextResponse mapToResponse(HistoricalContext context) {
        return HistoricalContextResponse.builder()
                .contextId(context.getContextId().toString())
                .name(context.getName())
                .description(context.getDescription())
                .era(context.getEra())
                .category(context.getCategory())
                .year(context.getYear())
                .startYear(context.getStartYear())
                .endYear(context.getEndYear())
                .period(context.getStartYear() != null && context.getEndYear() != null
                        ? context.getStartYear() + "\u2013" + context.getEndYear()
                        : null)
                .yearLabel(context.getYear() != null
                        ? context.getYear() + (Boolean.TRUE.equals(context.getBeforeTCN()) ? " TCN" : " SCN")
                        : null)
                .beforeTCN(context.getBeforeTCN())
                .location(context.getLocation())
                .imageUrl(context.getImageUrl())
                .createdBy(HistoricalContextResponse.CreatedByInfo.builder()
                        .uid(context.getCreatedBy().getUid().toString())
                        .userName(context.getCreatedBy().getUserName())
                        .build())
                .createdDate(context.getCreatedDate())
                .updatedDate(context.getUpdatedDate())
                .build();
    }
    
    /**
     * Helper method to map page to paginated response
     */
    private PaginatedResponse<HistoricalContextResponse> mapPageToPaginatedResponse(
            Page<HistoricalContext> page) {
        
        return PaginatedResponse.<HistoricalContextResponse>builder()
                .content(page.getContent().stream()
                        .map(this::mapToResponse)
                        .toList())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(page.getNumber())
                .pageSize(page.getSize())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build();
    }

        private String normalize(String value) {
                return value == null ? "" : value.trim().toLowerCase();
        }
}
