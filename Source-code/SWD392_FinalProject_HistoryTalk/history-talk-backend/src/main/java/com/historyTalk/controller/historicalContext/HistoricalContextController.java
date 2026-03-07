package com.historyTalk.controller.historicalContext;

import com.historyTalk.dto.ApiResponse;
import com.historyTalk.dto.PaginatedResponse;
import com.historyTalk.dto.historicalContext.CreateHistoricalContextRequest;
import com.historyTalk.dto.historicalContext.HistoricalContextResponse;
import com.historyTalk.dto.historicalContext.UpdateHistoricalContextRequest;
import com.historyTalk.entity.enums.EventCategory;
import com.historyTalk.entity.enums.EventEra;
import com.historyTalk.service.historicalContext.HistoricalContextService;
import com.historyTalk.utils.SecurityUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/historical-contexts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Historical Context", description = "API endpoints for managing historical contexts")
public class HistoricalContextController {
    
    private final HistoricalContextService contextService;
    
    /**
     * GET /v1/historical-contexts
     * Retrieve all historical contexts
     */
    @GetMapping
    @Operation(summary = "Get all historical contexts", description = "Retrieve paginated historical contexts with optional filters")
    public ResponseEntity<ApiResponse<PaginatedResponse<HistoricalContextResponse>>> getAllContexts(
            @RequestParam(required = false, defaultValue = "") String search,
            @RequestParam(required = false) EventEra era,
            @RequestParam(required = false) EventCategory category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "6") int limit) {
        
        log.info("GET /v1/historical-contexts - search: {}, era: {}, category: {}, page: {}, limit: {}",
                search, era, category, page, limit);
        
        var pageable = PageRequest.of(Math.max(page - 1, 0), limit, Sort.by(Sort.Direction.DESC, "createdDate"));
        var response = contextService.getAllContexts(search, era, category, pageable);
        
        return ResponseEntity.ok(ApiResponse.success(
                response,
                "Historical contexts retrieved successfully"
        ));
    }
    
    /**
     * GET /v1/historical-contexts/{contextId}
     * Retrieve a specific historical context by ID
     */
    @GetMapping("/{contextId}")
    @Operation(summary = "Get historical context by ID", description = "Retrieve details of a specific historical context")
    public ResponseEntity<ApiResponse<?>> getContextById(
            @PathVariable String contextId) {
        
        log.info("GET /v1/historical-contexts/{} ", contextId);
        
        var response = contextService.getContextById(contextId);
        
        return ResponseEntity.ok(ApiResponse.success(
                response,
                "Historical context retrieved successfully"
        ));
    }
    
    /**
     * POST /v1/historical-contexts
     * Create a new historical context (Staff only)
     */
    @PostMapping
//    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
//    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Create a new historical context", description = "Create a new historical context (Staff/Admin only)")
    public ResponseEntity<ApiResponse<?>> createContext(
            @Valid @RequestBody CreateHistoricalContextRequest request) {
        
        log.info("POST /v1/historical-contexts - Creating context: {}", request.getName());
        String staffId = SecurityUtils.getStaffId();
        var response = contextService.createContext(request, staffId);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                response,
                "Historical context created successfully"
        ));
    }
    
    /**
     * PUT /v1/historical-contexts/{contextId}
     * Update an existing historical context
     */
    @PutMapping("/{contextId}")
//    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
//    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update a historical context", description = "Update an existing historical context (Creator/Admin only)")
    public ResponseEntity<ApiResponse<?>> updateContext(
            @PathVariable String contextId,
            @Valid @RequestBody UpdateHistoricalContextRequest request) {
        
        log.info("PUT /v1/historical-contexts/{} - Updating context", contextId);
        String staffId = SecurityUtils.getStaffId();
        String staffRole = SecurityUtils.getRoleName();
        var response = contextService.updateContext(contextId, request, staffId, staffRole);
        
        return ResponseEntity.ok(ApiResponse.success(
                response,
                "Historical context updated successfully"
        ));
    }
    
        /**
         * DELETE /v1/historical-contexts/{contextId}
         * Delete a historical context
         */
    @DeleteMapping("/{contextId}")
//    @PreAuthorize("hasRole('STAFF') or hasRole('ADMIN')")
//    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete a historical context", description = "Delete a historical context (Creator/Admin only)")
    public ResponseEntity<Void> deleteContext(
            @PathVariable String contextId) {
        
        log.info("DELETE /v1/historical-contexts/{} - Deleting context", contextId);
        String staffId = SecurityUtils.getStaffId();
        String staffRole = SecurityUtils.getRoleName();
        contextService.deleteContext(contextId, staffId, staffRole);
        
        return ResponseEntity.noContent().build();
    }
}
