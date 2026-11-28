package com.historytalk.service.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.historytalk.dto.map.CreateMapPinRequest;
import com.historytalk.dto.map.MapPinResponse;
import com.historytalk.dto.map.UpdateMapPinRequest;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.map.MapPin;
import com.historytalk.entity.user.User;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.MapPinRepository;
import com.historytalk.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MapPinServiceImplTest {

    @Mock
    private MapPinRepository mapPinRepository;

    @Mock
    private HistoricalContextRepository contextRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MapPinServiceImpl mapPinService;

    @Test
    void createPin_savesPinWithoutGeoJson() {
        UUID ctxUuid = UUID.randomUUID();
        UUID callerUuid = UUID.randomUUID();
        HistoricalContext context = HistoricalContext.builder().contextId(ctxUuid).isPublished(true).build();
        User creator = User.builder().uid(callerUuid).build();
        when(contextRepository.findById(ctxUuid)).thenReturn(Optional.of(context));
        when(userRepository.findById(callerUuid)).thenReturn(Optional.of(creator));
        when(mapPinRepository.save(any(MapPin.class))).thenAnswer(inv -> {
            MapPin p = inv.getArgument(0);
            p.setPinId(UUID.randomUUID());
            return p;
        });

        CreateMapPinRequest request = new CreateMapPinRequest();
        request.setLabel(" Dien Bien Phu ");
        request.setLatitude(21.38);
        request.setLongitude(103.02);
        request.setPinYear(1954);

        MapPinResponse response = mapPinService.createPin(
                ctxUuid.toString(), request, callerUuid.toString(), "CONTENT_ADMIN");

        verify(mapPinRepository, times(1)).save(any(MapPin.class));
        assertThat(response.getPinOwnerType()).isEqualTo("ADMIN");
        assertThat(response.getLabel()).isEqualTo("Dien Bien Phu");
        assertThat(response.getLatitude()).isEqualTo(21.38);
        assertThat(response.getContextId()).isEqualTo(ctxUuid.toString());
    }

    @Test
    void jsonDoesNotExposePathGeoJson() throws Exception {
        MapPinResponse response = MapPinResponse.builder()
                .pinId(UUID.randomUUID().toString())
                .label("pin")
                .build();

        String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(response);

        assertThat(json).doesNotContain("pathGeoJson");
    }

    @Test
    void updatePin_regularUserOnUnpublishedContext_throwsNotFound() {
        UUID callerUuid = UUID.randomUUID();
        HistoricalContext context = context(false, null);
        MapPin pin = pin(context, callerUuid, "USER");
        when(mapPinRepository.findByPinIdAndDeletedAtIsNull(pin.getPinId())).thenReturn(Optional.of(pin));

        UpdateMapPinRequest request = new UpdateMapPinRequest();
        request.setLabel("New");
        String ctxId = context.getContextId().toString();

        assertThatThrownBy(() -> mapPinService.updatePin(
                ctxId, pin.getPinId().toString(), request, callerUuid.toString(), "CUSTOMER"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Không tìm thấy bối cảnh lịch sử với ID: " + ctxId);
        verify(mapPinRepository, never()).save(any());
    }

    @Test
    void deletePin_regularUserOnSoftDeletedContext_throwsNotFound() {
        UUID callerUuid = UUID.randomUUID();
        HistoricalContext context = context(true, LocalDateTime.now(ZoneOffset.UTC));
        MapPin pin = pin(context, callerUuid, "USER");
        when(mapPinRepository.findByPinIdAndDeletedAtIsNull(pin.getPinId())).thenReturn(Optional.of(pin));

        assertThatThrownBy(() -> mapPinService.deletePin(
                context.getContextId().toString(), pin.getPinId().toString(),
                callerUuid.toString(), "SCHOOL_STUDENT"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(mapPinRepository, never()).delete(any());
    }

    @Test
    void deletePin_regularUserOnPublishedContext_deletesOwnPin() {
        UUID callerUuid = UUID.randomUUID();
        HistoricalContext context = context(true, null);
        MapPin pin = pin(context, callerUuid, "USER");
        when(mapPinRepository.findByPinIdAndDeletedAtIsNull(pin.getPinId())).thenReturn(Optional.of(pin));

        mapPinService.deletePin(context.getContextId().toString(), pin.getPinId().toString(),
                callerUuid.toString(), "CUSTOMER");

        verify(mapPinRepository).delete(pin);
    }

    @Test
    void updatePin_contentAdminOnUnpublishedContext_updatesAdminPin() {
        UUID callerUuid = UUID.randomUUID();
        HistoricalContext context = context(false, null);
        MapPin pin = pin(context, callerUuid, "ADMIN");
        when(mapPinRepository.findByPinIdAndDeletedAtIsNull(pin.getPinId())).thenReturn(Optional.of(pin));
        when(mapPinRepository.save(any(MapPin.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateMapPinRequest request = new UpdateMapPinRequest();
        request.setLabel("New");

        MapPinResponse response = mapPinService.updatePin(context.getContextId().toString(),
                pin.getPinId().toString(), request, callerUuid.toString(), "CONTENT_ADMIN");

        assertThat(response.getLabel()).isEqualTo("New");
    }

    private HistoricalContext context(boolean published, LocalDateTime deletedAt) {
        return HistoricalContext.builder()
                .contextId(UUID.randomUUID())
                .isPublished(published)
                .deletedAt(deletedAt)
                .build();
    }

    private MapPin pin(HistoricalContext context, UUID ownerUid, String ownerType) {
        return MapPin.builder()
                .pinId(UUID.randomUUID())
                .historicalContext(context)
                .createdBy(User.builder().uid(ownerUid).build())
                .pinOwnerType(ownerType)
                .label("Old")
                .latitude(21.0)
                .longitude(105.0)
                .pinYear(1954)
                .build();
    }
}
