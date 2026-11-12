package com.historytalk.service.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.historytalk.dto.map.CreateMapPinRequest;
import com.historytalk.dto.map.MapPinResponse;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.map.MapPin;
import com.historytalk.entity.user.User;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.MapPinRepository;
import com.historytalk.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
}
