package com.historytalk.service.map;

import com.historytalk.dto.map.CreateMapFocusRequest;
import com.historytalk.dto.map.MapFocusResponse;
import com.historytalk.dto.map.ReorderMapFocusRequest;
import com.historytalk.dto.map.UpdateMapFocusRequest;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.map.MapFocus;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.MapFocusRepository;
import com.historytalk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MapFocusServiceImpl implements MapFocusService {

    private static final Sort ORDER_BY_INDEX = Sort.by(Sort.Direction.ASC, "orderIndex");

    private final MapFocusRepository    focusRepository;
    private final HistoricalContextRepository contextRepository;
    private final UserRepository        userRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // GET – role-aware focus listing
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<MapFocusResponse> getFocuses(String contextId, String role) {
        log.info("getFocuses contextId={} role={}", contextId, role);

        UUID ctxUuid = parseUuid(contextId, "context");
        HistoricalContext context = loadActiveContext(ctxUuid, contextId, role);

        List<MapFocus> focuses;
        if (isAdminRole(role)) {
            // Admin: all non-deleted focuses, including inactive
            focuses = focusRepository.findByHistoricalContext_ContextIdAndDeletedAtIsNull(
                    ctxUuid, ORDER_BY_INDEX);
        } else {
            // Guest / authenticated user: active-only
            focuses = focusRepository
                    .findByHistoricalContext_ContextIdAndDeletedAtIsNullAndIsActiveTrue(
                            ctxUuid, ORDER_BY_INDEX);
        }

        return focuses.stream().map(this::mapToResponse).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST – create a new focus point
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public MapFocusResponse createFocus(String contextId, CreateMapFocusRequest request,
                                        String callerId, String role) {
        log.info("createFocus contextId={} role={}", contextId, role);

        requireAdminRole(role);

        UUID ctxUuid = parseUuid(contextId, "context");
        HistoricalContext context = loadNonDeletedContext(ctxUuid, contextId);

        User creator = userRepository.findById(UUID.fromString(callerId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + callerId));

        // Auto-assign order_index as max + 1 (0 if no existing focuses)
        Integer maxIndex = focusRepository.findMaxOrderIndexByContextId(ctxUuid);
        int nextIndex = (maxIndex == null) ? 0 : maxIndex + 1;

        // Handle default flag: unset existing default if needed
        boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault());
        if (makeDefault) {
            unsetExistingDefault(ctxUuid);
        }

        MapFocus focus = MapFocus.builder()
                .historicalContext(context)
                .createdBy(creator)
                .name(request.getName().trim())
                .description(request.getDescription())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .zoomLevel(request.getZoomLevel() != null ? request.getZoomLevel() : 8.0)
                .orderIndex(nextIndex)
                .isDefault(makeDefault)
                .isActive(true)
                .build();

        MapFocus saved = focusRepository.save(focus);
        log.info("Map focus created: focusId={} orderIndex={} isDefault={}",
                saved.getFocusId(), saved.getOrderIndex(), saved.getIsDefault());
        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PUT – partial update (PATCH semantics)
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public MapFocusResponse updateFocus(String contextId, String focusId,
                                        UpdateMapFocusRequest request,
                                        String callerId, String role) {
        log.info("updateFocus focusId={} contextId={} role={}", focusId, contextId, role);

        requireAdminRole(role);

        UUID ctxUuid   = parseUuid(contextId, "context");
        UUID focusUuid = parseUuid(focusId, "focus");

        MapFocus focus = focusRepository.findByFocusIdAndDeletedAtIsNull(focusUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Map focus not found: " + focusId));

        // Verify focus belongs to this context
        if (!focus.getHistoricalContext().getContextId().equals(ctxUuid)) {
            throw new ResourceNotFoundException("Map focus not found: " + focusId);
        }

        // Apply non-null fields
        if (request.getName() != null) {
            focus.setName(request.getName().trim());
        }
        if (request.getDescription() != null) {
            focus.setDescription(request.getDescription());
        }
        if (request.getLatitude() != null) {
            focus.setLatitude(request.getLatitude());
        }
        if (request.getLongitude() != null) {
            focus.setLongitude(request.getLongitude());
        }
        if (request.getZoomLevel() != null) {
            focus.setZoomLevel(request.getZoomLevel());
        }
        if (request.getIsActive() != null) {
            focus.setIsActive(request.getIsActive());
        }
        if (request.getIsDefault() != null) {
            boolean makeDefault = Boolean.TRUE.equals(request.getIsDefault());
            if (makeDefault && !Boolean.TRUE.equals(focus.getIsDefault())) {
                unsetExistingDefault(ctxUuid);
            }
            focus.setIsDefault(makeDefault);
        }

        MapFocus updated = focusRepository.save(focus);
        log.info("Map focus updated: focusId={}", focusId);
        return mapToResponse(updated);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // PATCH /reorder – reorder all focuses for a context
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public List<MapFocusResponse> reorderFocuses(String contextId,
                                                  ReorderMapFocusRequest request,
                                                  String role) {
        log.info("reorderFocuses contextId={} count={} role={}",
                contextId, request.getFocusIds().size(), role);

        requireAdminRole(role);

        UUID ctxUuid = parseUuid(contextId, "context");
        loadNonDeletedContext(ctxUuid, contextId);    // verify context exists

        // Build a lookup map of all non-deleted focuses for this context
        Map<UUID, MapFocus> focusByUuid = focusRepository
                .findByHistoricalContext_ContextIdAndDeletedAtIsNull(ctxUuid)
                .stream()
                .collect(Collectors.toMap(MapFocus::getFocusId, Function.identity()));

        List<MapFocus> toSave = new ArrayList<>();
        for (int i = 0; i < request.getFocusIds().size(); i++) {
            UUID focusUuid = parseUuid(request.getFocusIds().get(i), "focus");
            MapFocus focus = focusByUuid.get(focusUuid);
            if (focus == null) {
                throw new ResourceNotFoundException(
                        "Map focus not found or does not belong to this context: "
                                + request.getFocusIds().get(i));
            }
            focus.setOrderIndex(i);
            toSave.add(focus);
        }

        List<MapFocus> saved = focusRepository.saveAll(toSave);
        log.info("Map focuses reordered: {} items for contextId={}", saved.size(), contextId);
        return saved.stream()
                .sorted(java.util.Comparator.comparing(MapFocus::getOrderIndex))
                .map(this::mapToResponse)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE – soft-delete a single focus
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deleteFocus(String contextId, String focusId, String role) {
        log.info("deleteFocus focusId={} contextId={} role={}", focusId, contextId, role);

        requireAdminRole(role);

        UUID ctxUuid   = parseUuid(contextId, "context");
        UUID focusUuid = parseUuid(focusId, "focus");

        MapFocus focus = focusRepository.findByFocusIdAndDeletedAtIsNull(focusUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Map focus not found: " + focusId));

        if (!focus.getHistoricalContext().getContextId().equals(ctxUuid)) {
            throw new ResourceNotFoundException("Map focus not found: " + focusId);
        }

        focus.setDeletedAt(LocalDateTime.now());
        focusRepository.save(focus);
        log.info("Map focus soft-deleted: focusId={}", focusId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CASCADE – called by HistoricalContextServiceImpl.softDeleteContext()
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void softDeleteAllFocusesForContext(String contextId) {
        UUID ctxUuid = parseUuid(contextId, "context");
        List<MapFocus> focuses =
                focusRepository.findByHistoricalContext_ContextIdAndDeletedAtIsNull(ctxUuid);
        if (focuses.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        focuses.forEach(f -> f.setDeletedAt(now));
        focusRepository.saveAll(focuses);
        log.info("Cascade soft-deleted {} map focuses for contextId={}", focuses.size(), contextId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Unset {@code is_default} on the current default focus for a context, if any.
     * Must be called inside an active transaction.
     */
    private void unsetExistingDefault(UUID ctxUuid) {
        focusRepository
                .findByHistoricalContext_ContextIdAndIsDefaultTrueAndDeletedAtIsNull(ctxUuid)
                .ifPresent(existing -> {
                    existing.setIsDefault(false);
                    focusRepository.save(existing);
                });
    }

    /**
     * Load a context that is accessible to the given role.
     * Non-admins only see published, non-deleted contexts.
     */
    private HistoricalContext loadActiveContext(UUID ctxUuid, String contextId, String role) {
        HistoricalContext context = contextRepository.findById(ctxUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bối cảnh lịch sử với ID: " + contextId));

        if (!isAdminRole(role)) {
            if (context.getDeletedAt() != null || !Boolean.TRUE.equals(context.getIsPublished())) {
                throw new ResourceNotFoundException(
                        "Không tìm thấy bối cảnh lịch sử với ID: " + contextId);
            }
        }
        return context;
    }

    /** Load a non-deleted context regardless of published state (admin writes). */
    private HistoricalContext loadNonDeletedContext(UUID ctxUuid, String contextId) {
        HistoricalContext context = contextRepository.findById(ctxUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bối cảnh lịch sử với ID: " + contextId));
        if (context.getDeletedAt() != null) {
            throw new ResourceNotFoundException(
                    "Không tìm thấy bối cảnh lịch sử với ID: " + contextId);
        }
        return context;
    }

    private MapFocusResponse mapToResponse(MapFocus focus) {
        return MapFocusResponse.builder()
                .focusId(focus.getFocusId().toString())
                .contextId(focus.getHistoricalContext().getContextId().toString())
                .name(focus.getName())
                .description(focus.getDescription())
                .latitude(focus.getLatitude())
                .longitude(focus.getLongitude())
                .zoomLevel(focus.getZoomLevel())
                .orderIndex(focus.getOrderIndex())
                .isDefault(focus.getIsDefault())
                .isActive(focus.getIsActive())
                .createdBy(focus.getCreatedBy().getUid().toString())
                .createdAt(focus.getCreatedAt())
                .updatedAt(focus.getUpdatedAt())
                .build();
    }

    private void requireAdminRole(String role) {
        if (!isAdminRole(role)) {
            throw new InvalidRequestException(
                    "Bạn không có quyền thực hiện thao tác này");
        }
    }

    private boolean isAdminRole(String role) {
        return role != null && (
                "CONTENT_ADMIN".equalsIgnoreCase(role)
                || "SYSTEM_ADMIN".equalsIgnoreCase(role)
                || "STAFF".equalsIgnoreCase(role)
                || "ADMIN".equalsIgnoreCase(role)
        );
    }

    private UUID parseUuid(String value, String label) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new InvalidRequestException("Invalid " + label + " ID format: " + value);
        }
    }
}
