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
            String search, EventEra era, EventCategory category, Pageable pageable, String role) {
        
        log.info("Fetching historical contexts with search: {}, era: {}, category: {}", search, era, category);
        
        boolean includeDraft = isStaffOrAdmin(role);
        boolean includeDeleted = isStaffOrAdmin(role);
        Page<HistoricalContext> page = contextRepository
            .findAllWithSearch(normalize(search), era, category, includeDraft, includeDeleted, pageable);
        
        return mapPageToPaginatedResponse(page);
    }

    /**
     * Get all historical contexts as simple list (no pagination)
     */
    @Transactional(readOnly = true)
    public List<HistoricalContextResponse> getAllContextsSimple(String search, String role) {
        log.info("Fetching all historical contexts with search: {}", search);
        boolean includeDraft = isStaffOrAdmin(role);
        boolean includeDeleted = isStaffOrAdmin(role);
        return contextRepository
                .findAllSimple(normalize(search), includeDraft, includeDeleted)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    
    /**
     * Get a specific historical context by ID
     */
    @Transactional(readOnly = true)
    public HistoricalContextResponse getContextById(String contextId, String role) {
        log.info("Fetching historical context with ID: {}", contextId);
        
        HistoricalContext context = contextRepository.findById(UUID.fromString(contextId))
                .orElseThrow(() -> new ResourceNotFoundException("Historical context not found with ID: "+contextId));

        if (!isStaffOrAdmin(role) && Boolean.TRUE.equals(context.getIsDraft())) {
            throw new ResourceNotFoundException("Historical context not found with ID: "+contextId);
        }
        
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
                .videoUrl(request.getVideoUrl())
            .isDraft(request.getIsDraft() != null ? request.getIsDraft() : true)
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
        if (request.getVideoUrl() != null) {
            context.setVideoUrl(request.getVideoUrl());
        }
        if (request.getIsDraft() != null) {
            context.setIsDraft(request.getIsDraft());
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
     * Soft delete a historical context and trigger manual cascade to its children
     */
    @Transactional
    public void softDeleteContext(String contextId, String userId, String userRole) {
        log.info("Soft deleting historical context with ID: {}", contextId);
        
        HistoricalContext context = contextRepository.findById(UUID.fromString(contextId))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Not found with ID: " + contextId
                ));
        
        if (!context.getCreatedBy().getUid().equals(UUID.fromString(userId)) && !"ADMIN".equalsIgnoreCase(userRole)) {
            throw new InvalidRequestException(
                    "You do not have permission to soft delete this historical context"
            );
        }
        
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        context.setDeletedAt(now);
        contextRepository.save(context);

        // Cascade to Documents
        if (context.getDocuments() != null) {
            context.getDocuments().forEach(doc -> doc.setDeletedAt(now));
        }

        // Cascade to Characters and their children
        if (context.getCharacters() != null) {
            context.getCharacters().forEach(character -> {
                character.setDeletedAt(now);
                if (character.getDocuments() != null) {
                    character.getDocuments().forEach(doc -> doc.setDeletedAt(now));
                }
                if (character.getChatSessions() != null) {
                    character.getChatSessions().forEach(session -> {
                        session.setDeletedAt(now);
                        if (session.getMessages() != null) {
                            session.getMessages().forEach(msg -> msg.setDeletedAt(now));
                        }
                    });
                }
            });
        }

        // Cascade to Quizzes and their children
        if (context.getQuizzes() != null) {
            context.getQuizzes().forEach(quiz -> {
                quiz.setDeletedAt(now);
                if (quiz.getQuestions() != null) {
                    quiz.getQuestions().forEach(q -> q.setDeletedAt(now));
                }
                if (quiz.getQuizResults() != null) {
                    quiz.getQuizResults().forEach(result -> {
                        result.setDeletedAt(now);
                        if (result.getAnswerDetails() != null) {
                            result.getAnswerDetails().forEach(detail -> detail.setDeletedAt(now));
                        }
                    });
                }
                // Note: QuizSessions aren't directly fetched via Quiz mapping usually, 
                // but handled in @Where or soft-deleted individually. 
                // Currently Quiz entity does not map quizSessions.
            });
        }
        
        log.info("Historical context soft-deleted successfully with ID: {}", contextId);
    }

    @Transactional(readOnly = true)
    public List<HistoricalContextResponse> getDeletedContexts() {
        return contextRepository.findAllDeleted().stream()
                .map(this::mapToResponseWithInactive)
                .toList();
    }

    @Transactional
    public void restoreContext(String contextId) {
        int updated = contextRepository.restoreById(UUID.fromString(contextId));
        if (updated == 0) {
            throw new ResourceNotFoundException("Historical context not found with ID: " + contextId);
        }
    }

    @Transactional
    public void permanentDeleteContext(String contextId) {
        contextRepository.deleteById(UUID.fromString(contextId));
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
                .videoUrl(context.getVideoUrl())
            .isDraft(context.getIsDraft())
            .status(buildStatus(context.getIsDraft(), context.getDeletedAt()))
                .createdBy(HistoricalContextResponse.CreatedByInfo.builder()
                        .uid(context.getCreatedBy().getUid().toString())
                        .userName(context.getCreatedBy().getUserName())
                        .build())
                .createdDate(context.getCreatedDate())
                .updatedDate(context.getUpdatedDate())
                .deletedAt(context.getDeletedAt())
                .build();
    }

        private HistoricalContextResponse mapToResponseWithInactive(HistoricalContext context) {
        HistoricalContextResponse response = mapToResponse(context);
        response.setStatus(buildStatus(context.getIsDraft(), context.getDeletedAt()));
        return response;
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

        private boolean isStaffOrAdmin(String role) {
            return role != null && ("STAFF".equalsIgnoreCase(role) || "ADMIN".equalsIgnoreCase(role));
        }

        private String buildStatus(Boolean isDraft, java.time.LocalDateTime deletedAt) {
            if (deletedAt != null) {
                return "INACTIVE";
            }
            if (Boolean.TRUE.equals(isDraft)) {
                return "DRAFT";
            }
            return "ACTIVE";
        }
}
