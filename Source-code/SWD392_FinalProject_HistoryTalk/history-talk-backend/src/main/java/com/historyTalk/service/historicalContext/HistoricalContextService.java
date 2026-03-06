package com.historyTalk.service.historicalContext;

import com.historyTalk.dto.CreateHistoricalContextRequest;
import com.historyTalk.dto.HistoricalContextResponse;
import com.historyTalk.dto.PaginatedResponse;
import com.historyTalk.dto.UpdateHistoricalContextRequest;
import com.historyTalk.entity.HistoricalContext;
import com.historyTalk.entity.Staff;
import com.historyTalk.exception.DuplicateResourceException;
import com.historyTalk.exception.ForbiddenException;
import com.historyTalk.exception.ResourceNotFoundException;
import com.historyTalk.repository.HistoricalContextRepository;
import com.historyTalk.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class HistoricalContextService {

        private final HistoricalContextRepository contextRepository;
        private final StaffRepository staffRepository;
    
    /**
     * Get all historical contexts with pagination and search
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<HistoricalContextResponse> getAllContexts(
            String search, Pageable pageable) {
        
        log.info("Fetching historical contexts with search: {}", search);
        
        Page<HistoricalContext> page = contextRepository
                .findAllWithSearch(normalize(search), pageable);
        
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
        
        HistoricalContext context = contextRepository.findById(contextId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Historical Context", "contextId", contextId
                ));
        
        return mapToResponse(context);
    }
    
    /**
     * Create a new historical context (Staff only)
     */
    @Transactional
    public HistoricalContextResponse createContext(
            CreateHistoricalContextRequest request, String staffId) {
        
        log.info("Creating historical context: {} by staff: {}", request.getName(), staffId);
        
        // Check for duplicate name
        if (contextRepository.findByNameIgnoreCase(request.getName()).isPresent()) {
            throw new DuplicateResourceException("Historical Context", "name", request.getName());
        }

        Staff staff = staffRepository.findById(staffId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff", "staffId", staffId));

        HistoricalContext context = HistoricalContext.builder()
                .name(request.getName())
                .description(request.getDescription())
                .staff(staff)
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
            String staffId, String staffRole) {
        
        log.info("Updating historical context with ID: {}", contextId);
        
        HistoricalContext context = contextRepository.findById(contextId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Historical Context", "contextId", contextId
                ));
        
        // Check if user has permission to update (creator or admin)
        if (!context.getStaff().getStaffId().equals(staffId) && !"ADMIN".equalsIgnoreCase(staffRole)) {
            throw new ForbiddenException(
                    "You do not have permission to update this historical context"
            );
        }
        
        // Check for duplicate name (if name is being changed)
        if (request.getName() != null && 
            !request.getName().equalsIgnoreCase(context.getName()) &&
            contextRepository.existsByNameIgnoreCaseAndContextIdNot(request.getName(), contextId)) {
            throw new DuplicateResourceException("Historical Context", "name", request.getName());
        }
        
        // Update fields
        if (request.getName() != null) {
            context.setName(request.getName());
        }
        if (request.getDescription() != null) {
            context.setDescription(request.getDescription());
        }
        HistoricalContext updatedContext = contextRepository.save(context);
        log.info("Historical context updated successfully with ID: {}", contextId);
        
        return mapToResponse(updatedContext);
    }
    
        /**
         * Delete a historical context (only creator or admin)
         */
    @Transactional
    public void deleteContext(String contextId, String staffId, String staffRole) {
        log.info("Deleting historical context with ID: {}", contextId);
        
        HistoricalContext context = contextRepository.findById(contextId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Historical Context", "contextId", contextId
                ));
        
        // Check if user has permission to delete (creator or admin)
        if (!context.getStaff().getStaffId().equals(staffId) && !"ADMIN".equalsIgnoreCase(staffRole)) {
            throw new ForbiddenException(
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
                .contextId(context.getContextId())
                .name(context.getName())
                .description(context.getDescription())
                .createdBy(HistoricalContextResponse.CreatedByInfo.builder()
                        .staffId(context.getStaff().getStaffId())
                        .name(context.getStaff().getName())
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
                return value == null ? "" : value.trim();
        }
}
