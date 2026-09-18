package com.historytalk.service.map;

import com.historytalk.dto.map.CreateMapPinRequest;
import com.historytalk.dto.map.MapPinResponse;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.map.MapPin;
import com.historytalk.entity.user.User;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.MapPinRepository;
import com.historytalk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class MapPinServiceImpl implements MapPinService {

    private static final String PIN_OWNER_ADMIN = "ADMIN";
    private static final String PIN_OWNER_USER  = "USER";

    private static final Set<String> VALID_PIN_TYPES = Set.of("ALLIED_FORCE", "ENEMY_FORCE");

    private final MapPinRepository    mapPinRepository;
    private final HistoricalContextRepository contextRepository;
    private final UserRepository      userRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // GET – role-aware pin loading
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<MapPinResponse> getPins(String contextId, Integer year, String callerId, String role) {
        log.info("getPins contextId={} year={} callerId={} role={}", contextId, year, callerId, role);

        UUID ctxUuid = parseUuid(contextId, "context");

        // Verify context exists and is accessible
        HistoricalContext context = contextRepository.findById(ctxUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bối cảnh lịch sử với ID: " + contextId));

        // Non-admin callers can only query published contexts
        if (!isAdminRole(role) && context.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Không tìm thấy bối cảnh lịch sử với ID: " + contextId);
        }
        if (!isAdminRole(role) && !Boolean.TRUE.equals(context.getIsPublished())) {
            throw new ResourceNotFoundException("Không tìm thấy bối cảnh lịch sử với ID: " + contextId);
        }

        List<MapPin> pins;
        if (callerId != null && !isAdminRole(role)) {
            // Authenticated regular user: admin pins + own user pins
            pins = mapPinRepository.findVisiblePinsForUser(ctxUuid, year, UUID.fromString(callerId));
        } else {
            // Guest (callerId=null) or admin: ADMIN pins only
            pins = mapPinRepository.findAdminPinsByContextAndYear(ctxUuid, year);
        }

        return pins.stream().map(this::mapToResponse).toList();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST – role-aware pin creation
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public MapPinResponse createPin(String contextId, CreateMapPinRequest request,
                                    String callerId, String role) {
        log.info("createPin contextId={} role={}", contextId, role);

        UUID ctxUuid = parseUuid(contextId, "context");

        HistoricalContext context = contextRepository.findById(ctxUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy bối cảnh lịch sử với ID: " + contextId));

        if (context.getDeletedAt() != null) {
            throw new ResourceNotFoundException("Không tìm thấy bối cảnh lịch sử với ID: " + contextId);
        }

        User creator = userRepository.findById(UUID.fromString(callerId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + callerId));

        String pinOwnerType;
        String pinType;

        if (isAdminRole(role)) {
            // Admin: context may be draft; pinType is required and validated
            pinOwnerType = PIN_OWNER_ADMIN;
            if (request.getPinType() == null || !VALID_PIN_TYPES.contains(request.getPinType().toUpperCase())) {
                throw new InvalidRequestException(
                        "pinType phải là ALLIED_FORCE hoặc ENEMY_FORCE cho pin của admin");
            }
            pinType = request.getPinType().toUpperCase();
        } else {
            // Regular user: context must be published; pinType is ignored
            if (!Boolean.TRUE.equals(context.getIsPublished())) {
                throw new ResourceNotFoundException(
                        "Không tìm thấy bối cảnh lịch sử với ID: " + contextId);
            }
            pinOwnerType = PIN_OWNER_USER;
            pinType = null;
        }

        MapPin pin = MapPin.builder()
                .historicalContext(context)
                .createdBy(creator)
                .pinOwnerType(pinOwnerType)
                .label(request.getLabel().trim())
                .description(request.getDescription())
                .pinType(pinType)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .pinYear(request.getPinYear())
                .build();

        MapPin saved = mapPinRepository.save(pin);
        log.info("Map pin created: pinId={} pinOwnerType={}", saved.getPinId(), saved.getPinOwnerType());
        return mapToResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE – role-aware hard delete
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void deletePin(String contextId, String pinId, String callerId, String role) {
        log.info("deletePin pinId={} contextId={} role={}", pinId, contextId, role);

        UUID pinUuid = parseUuid(pinId, "pin");
        UUID ctxUuid = parseUuid(contextId, "context");

        MapPin pin = mapPinRepository.findByPinIdAndDeletedAtIsNull(pinUuid)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Không tìm thấy map pin với ID: " + pinId));

        // Verify pin belongs to the given context
        if (!pin.getHistoricalContext().getContextId().equals(ctxUuid)) {
            throw new ResourceNotFoundException("Không tìm thấy map pin với ID: " + pinId);
        }

        if (isAdminRole(role)) {
            // Admins can only delete ADMIN pins, not user's personal pins
            if (!PIN_OWNER_ADMIN.equals(pin.getPinOwnerType())) {
                throw new ResourceNotFoundException("Không tìm thấy map pin với ID: " + pinId);
            }
        } else {
            // Regular users can only delete their own USER pins
            if (!PIN_OWNER_USER.equals(pin.getPinOwnerType())
                    || !pin.getCreatedBy().getUid().toString().equals(callerId)) {
                throw new ResourceNotFoundException("Không tìm thấy map pin với ID: " + pinId);
            }
        }

        mapPinRepository.delete(pin);
        log.info("Map pin hard-deleted: pinId={}", pinId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // CASCADE – called by HistoricalContextServiceImpl.softDeleteContext()
    // ─────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void softDeleteAllPinsForContext(String contextId) {
        UUID ctxUuid = parseUuid(contextId, "context");
        List<MapPin> pins = mapPinRepository.findByHistoricalContext_ContextIdAndDeletedAtIsNull(ctxUuid);
        if (pins.isEmpty()) return;

        LocalDateTime now = LocalDateTime.now();
        pins.forEach(p -> p.setDeletedAt(now));
        mapPinRepository.saveAll(pins);
        log.info("Cascade soft-deleted {} map pins for contextId={}", pins.size(), contextId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private MapPinResponse mapToResponse(MapPin pin) {
        return MapPinResponse.builder()
                .pinId(pin.getPinId().toString())
                .contextId(pin.getHistoricalContext().getContextId().toString())
                .createdBy(pin.getCreatedBy().getUid().toString())
                .pinOwnerType(pin.getPinOwnerType())
                .label(pin.getLabel())
                .description(pin.getDescription())
                .pinType(pin.getPinType())
                .latitude(pin.getLatitude())
                .longitude(pin.getLongitude())
                .pinYear(pin.getPinYear())
                .createdAt(pin.getCreatedAt())
                .updatedAt(pin.getUpdatedAt())
                .build();
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
