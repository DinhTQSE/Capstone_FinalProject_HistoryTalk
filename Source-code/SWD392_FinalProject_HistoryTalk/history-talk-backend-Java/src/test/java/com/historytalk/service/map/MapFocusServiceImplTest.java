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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

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

    private UUID contextId;
    private UUID adminId;
    private HistoricalContext publishedContext;
    private User adminUser;

    @BeforeEach
    void setUp() {
        contextId = UUID.randomUUID();
        adminId = UUID.randomUUID();

        publishedContext = HistoricalContext.builder()
                .contextId(contextId)
                .name("Chiến dịch Điện Biên Phủ 1954")
                .isPublished(true)
                .build();

        adminUser = User.builder()
                .uid(adminId)
                .userName("content_admin")
                .build();
    }

    @Test
    @DisplayName("WF-06 / DS-06a: Regular user lists active map focus camera positions ordered by orderIndex")
    void getFocuses_RegularUser_ReturnsActiveFocusesOrderedByIndex() {
        MapFocus focus = MapFocus.builder()
                .focusId(UUID.randomUUID())
                .historicalContext(publishedContext)
                .createdBy(adminUser)
                .name("Đồi A1")
                .latitude(21.385)
                .longitude(103.021)
                .zoomLevel(14.0)
                .orderIndex(0)
                .isDefault(true)
                .isActive(true)
                .build();

        when(contextRepository.findById(contextId)).thenReturn(Optional.of(publishedContext));
        when(focusRepository.findByHistoricalContext_ContextIdAndDeletedAtIsNullAndIsActiveTrue(
                eq(contextId), any(Sort.class))).thenReturn(List.of(focus));

        List<MapFocusResponse> result = mapFocusService.getFocuses(contextId.toString(), "SCHOOL_STUDENT");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Đồi A1");
        assertThat(result.get(0).getZoomLevel()).isEqualTo(14.0);
        assertThat(result.get(0).getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("WF-06 / DS-06a: Content Admin creates new map focus and unsets previous default focus")
    void createFocus_ContentAdmin_AutoAssignsNextOrderIndexAndUnsetsExistingDefault() {
        MapFocus existingDefault = MapFocus.builder()
                .focusId(UUID.randomUUID())
                .historicalContext(publishedContext)
                .createdBy(adminUser)
                .name("Mường Thanh")
                .isDefault(true)
                .orderIndex(0)
                .build();

        CreateMapFocusRequest request = new CreateMapFocusRequest();
        request.setName("Đồi Him Lam");
        request.setDescription("Trung tâm đề kháng Him Lam mở màn chiến dịch");
        request.setLatitude(21.401);
        request.setLongitude(103.035);
        request.setZoomLevel(13.5);
        request.setIsDefault(true);

        when(contextRepository.findById(contextId)).thenReturn(Optional.of(publishedContext));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(focusRepository.findMaxOrderIndexByContextId(contextId)).thenReturn(0);
        when(focusRepository.findByHistoricalContext_ContextIdAndIsDefaultTrueAndDeletedAtIsNull(contextId))
                .thenReturn(Optional.of(existingDefault));
        when(focusRepository.save(any(MapFocus.class))).thenAnswer(inv -> {
            MapFocus f = inv.getArgument(0);
            if (f.getFocusId() == null) {
                f.setFocusId(UUID.randomUUID());
            }
            return f;
        });

        MapFocusResponse response = mapFocusService.createFocus(
                contextId.toString(), request, adminId.toString(), "CONTENT_ADMIN");

        assertThat(existingDefault.getIsDefault()).isFalse();
        assertThat(response.getName()).isEqualTo("Đồi Him Lam");
        assertThat(response.getOrderIndex()).isEqualTo(1);
        assertThat(response.getIsDefault()).isTrue();
    }

    @Test
    @DisplayName("WF-06 / DS-06b: Non-admin caller attempting to create map focus is rejected")
    void createFocus_NonAdminCaller_ThrowsInvalidRequestException() {
        CreateMapFocusRequest request = new CreateMapFocusRequest();
        request.setName("Trái phép");
        request.setLatitude(21.0);
        request.setLongitude(105.0);

        when(contextRepository.findById(contextId)).thenReturn(Optional.of(publishedContext));

        assertThatThrownBy(() -> mapFocusService.createFocus(
                contextId.toString(), request, UUID.randomUUID().toString(), "SCHOOL_STUDENT"))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("WF-06: Content Admin reorders map focus points")
    void reorderFocuses_ContentAdmin_UpdatesOrderIndices() {
        UUID focus1Id = UUID.randomUUID();
        UUID focus2Id = UUID.randomUUID();

        MapFocus f1 = MapFocus.builder()
                .focusId(focus1Id)
                .historicalContext(publishedContext)
                .createdBy(adminUser)
                .name("Focus 1")
                .orderIndex(0)
                .build();
        MapFocus f2 = MapFocus.builder()
                .focusId(focus2Id)
                .historicalContext(publishedContext)
                .createdBy(adminUser)
                .name("Focus 2")
                .orderIndex(1)
                .build();

        ReorderMapFocusRequest request = new ReorderMapFocusRequest();
        request.setFocusIds(List.of(focus2Id.toString(), focus1Id.toString()));

        when(contextRepository.findById(contextId)).thenReturn(Optional.of(publishedContext));
        when(focusRepository.findByHistoricalContext_ContextIdAndDeletedAtIsNull(contextId))
                .thenReturn(List.of(f1, f2));
        when(focusRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        List<MapFocusResponse> reordered = mapFocusService.reorderFocuses(
                contextId.toString(), request, "CONTENT_ADMIN");

        assertThat(reordered).hasSize(2);
        assertThat(reordered.get(0).getFocusId()).isEqualTo(focus2Id.toString());
        assertThat(reordered.get(0).getOrderIndex()).isEqualTo(0);
        assertThat(reordered.get(1).getFocusId()).isEqualTo(focus1Id.toString());
        assertThat(reordered.get(1).getOrderIndex()).isEqualTo(1);
    }

    @Test
    @DisplayName("WF-06: Content Admin soft-deletes a map focus point")
    void deleteFocus_ContentAdmin_SoftDeletesFocus() {
        UUID focusId = UUID.randomUUID();
        MapFocus focus = MapFocus.builder()
                .focusId(focusId)
                .historicalContext(publishedContext)
                .createdBy(adminUser)
                .name("Đồi C1")
                .build();

        when(contextRepository.findById(contextId)).thenReturn(Optional.of(publishedContext));
        when(focusRepository.findByFocusIdAndDeletedAtIsNull(focusId)).thenReturn(Optional.of(focus));

        mapFocusService.deleteFocus(contextId.toString(), focusId.toString(), "CONTENT_ADMIN");

        assertThat(focus.getDeletedAt()).isNotNull();
        verify(focusRepository).save(focus);
    }
}
