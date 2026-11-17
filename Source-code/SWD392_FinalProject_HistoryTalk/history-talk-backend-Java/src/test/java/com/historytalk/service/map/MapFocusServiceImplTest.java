package com.historytalk.service.map;

import com.historytalk.dto.map.CreateMapFocusRequest;
import com.historytalk.dto.map.MapFocusResponse;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.map.MapFocus;
import com.historytalk.entity.user.User;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.MapFocusRepository;
import com.historytalk.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MapFocusServiceImplTest {

    @Mock
    private MapFocusRepository focusRepository;

    @Mock
    private HistoricalContextRepository contextRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private MapFocusServiceImpl mapFocusService;

    @Test
    void createFocus_storesContextAndCreatorAsUuid() {
        UUID ctxUuid = UUID.randomUUID();
        UUID callerUuid = UUID.randomUUID();
        when(contextRepository.findById(ctxUuid))
                .thenReturn(Optional.of(HistoricalContext.builder().contextId(ctxUuid).build()));
        when(userRepository.findById(callerUuid))
                .thenReturn(Optional.of(User.builder().uid(callerUuid).build()));
        when(focusRepository.save(any(MapFocus.class))).thenAnswer(inv -> {
            MapFocus f = inv.getArgument(0);
            f.setFocusId(UUID.randomUUID());
            return f;
        });

        CreateMapFocusRequest request = new CreateMapFocusRequest();
        request.setName("  A1 Hill ");
        request.setLatitude(21.38);
        request.setLongitude(103.02);

        MapFocusResponse response = mapFocusService.createFocus(
                ctxUuid.toString(), request, callerUuid.toString(), "SYSTEM_ADMIN");

        ArgumentCaptor<MapFocus> saved = ArgumentCaptor.forClass(MapFocus.class);
        verify(focusRepository).save(saved.capture());
        assertThat(saved.getValue().getContextId()).isEqualTo(ctxUuid);
        assertThat(saved.getValue().getCreatedBy()).isEqualTo(callerUuid);
        assertThat(response.getContextId()).isEqualTo(ctxUuid.toString());
        assertThat(response.getCreatedBy()).isEqualTo(callerUuid.toString());
        assertThat(response.getName()).isEqualTo("A1 Hill");
    }

    @Test
    void deleteFocus_rejectsFocusFromAnotherContext() {
        UUID ctxUuid = UUID.randomUUID();
        UUID focusUuid = UUID.randomUUID();
        when(contextRepository.findById(ctxUuid))
                .thenReturn(Optional.of(HistoricalContext.builder().contextId(ctxUuid).build()));
        when(focusRepository.findByFocusIdAndDeletedAtIsNull(focusUuid))
                .thenReturn(Optional.of(MapFocus.builder()
                        .focusId(focusUuid)
                        .contextId(UUID.randomUUID())
                        .createdBy(UUID.randomUUID())
                        .build()));

        assertThatThrownBy(() -> mapFocusService.deleteFocus(
                ctxUuid.toString(), focusUuid.toString(), "SYSTEM_ADMIN"))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(focusRepository, never()).save(any());
    }

    @Test
    void getFocuses_unknownContext_throwsNotFound() {
        UUID ctxUuid = UUID.randomUUID();
        when(contextRepository.findById(ctxUuid)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> mapFocusService.getFocuses(ctxUuid.toString(), "CUSTOMER"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
