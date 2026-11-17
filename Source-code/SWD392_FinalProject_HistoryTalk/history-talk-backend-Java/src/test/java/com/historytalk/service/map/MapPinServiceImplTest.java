package com.historytalk.service.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.map.CreateMapPinRequest;
import com.historytalk.dto.map.MapPinResponse;
import com.historytalk.dto.map.UpdateMapPinRequest;
import com.historytalk.entity.historicalContext.HistoricalContext;
import com.historytalk.entity.map.MapPin;
import com.historytalk.entity.school.School;
import com.historytalk.entity.user.User;
import com.historytalk.exception.ForbiddenException;
import com.historytalk.exception.InvalidRequestException;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.repository.HistoricalContextRepository;
import com.historytalk.repository.MapPinRepository;
import com.historytalk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MapPinServiceImplTest {

    @Mock
    private MapPinRepository mapPinRepository;

    @Mock
    private HistoricalContextRepository contextRepository;

    @Mock
    private UserRepository userRepository;

    private MapPinServiceImpl mapPinService;

    private UUID contextId;
    private UUID adminId;
    private UUID studentId;
    private HistoricalContext publishedContext;
    private User adminUser;
    private User studentUser;

    @BeforeEach
    void setUp() {
        mapPinService = new MapPinServiceImpl(
                mapPinRepository,
                contextRepository,
                userRepository,
                new ObjectMapper()
        );

        contextId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        studentId = UUID.randomUUID();

        publishedContext = HistoricalContext.builder()
                .contextId(contextId)
                .name("Chiến thắng Bạch Đằng 1288")
                .isPublished(true)
                .build();

        adminUser = User.builder()
                .uid(adminId)
                .userName("content_admin")
                .build();

        studentUser = User.builder()
                .uid(studentId)
                .userName("student_01")
                .build();
    }

    @Test
    @DisplayName("WF-06 / DS-06a: Guest loads ADMIN pins for published historical context and year")
    void getPins_Guest_ReturnsAdminPinsOnly() {
        MapPin adminPin = MapPin.builder()
                .pinId(UUID.randomUUID())
                .historicalContext(publishedContext)
                .createdBy(adminUser)
                .pinOwnerType("ADMIN")
                .label("Bãi cọc Yên Giang")
                .description("Trận địa cọc ngầm trên sông Bạch Đằng")
                .latitude(20.9412)
                .longitude(106.8123)
                .pinYear(1288)
                .pathGeoJson("{\"type\":\"LineString\",\"coordinates\":[[106.80,20.93],[106.81,20.94]]}")
                .build();

        when(contextRepository.findById(contextId)).thenReturn(Optional.of(publishedContext));
        when(mapPinRepository.findAdminPinsByContextAndYear(contextId, 1288)).thenReturn(List.of(adminPin));

        List<MapPinResponse> result = mapPinService.getPins(contextId.toString(), 1288, null, "USER");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLabel()).isEqualTo("Bãi cọc Yên Giang");
        assertThat(result.get(0).getPinOwnerType()).isEqualTo("ADMIN");
        assertThat(result.get(0).getPinYear()).isEqualTo(1288);
        verify(mapPinRepository).findAdminPinsByContextAndYear(contextId, 1288);
    }

    @Test
    @DisplayName("WF-06 / F-12: Authenticated student loads both official ADMIN pins and personal USER pins")
    void getPins_AuthenticatedStudent_ReturnsAdminAndOwnUserPins() {
        MapPin userPin = MapPin.builder()
                .pinId(UUID.randomUUID())
                .historicalContext(publishedContext)
                .createdBy(studentUser)
                .pinOwnerType("USER")
                .label("Ghi chú học tập của học sinh")
                .latitude(20.95)
                .longitude(106.82)
                .pinYear(1288)
                .pathGeoJson("{\"type\":\"LineString\",\"coordinates\":[]}")
                .build();

        when(contextRepository.findById(contextId)).thenReturn(Optional.of(publishedContext));
        when(mapPinRepository.findVisiblePinsForUser(contextId, 1288, studentId)).thenReturn(List.of(userPin));

        List<MapPinResponse> result = mapPinService.getPins(
                contextId.toString(), 1288, studentId.toString(), "SCHOOL_STUDENT");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPinOwnerType()).isEqualTo("USER");
        verify(mapPinRepository).findVisiblePinsForUser(contextId, 1288, studentId);
    }

    @Test
    @DisplayName("WF-06: Regular user cannot load pins for unpublished historical context")
    void getPins_UnpublishedContextForRegularUser_ThrowsNotFound() {
        publishedContext.setIsPublished(false);
        when(contextRepository.findById(contextId)).thenReturn(Optional.of(publishedContext));

        assertThatThrownBy(() -> mapPinService.getPins(
                contextId.toString(), 1288, studentId.toString(), "SCHOOL_STUDENT"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("WF-06 / N3: Outsider student from School B cannot load pins of School A context")
    void getPins_CrossSchoolContextForOutsider_ThrowsNotFound() {
        School schoolA = School.builder().id(UUID.randomUUID()).name("THPT A").build();
        School schoolB = School.builder().id(UUID.randomUUID()).name("THPT B").build();
        publishedContext.setSchool(schoolA);
        studentUser.setSchool(schoolB);

        when(contextRepository.findById(contextId)).thenReturn(Optional.of(publishedContext));
        when(userRepository.findById(studentId)).thenReturn(Optional.of(studentUser));

        assertThatThrownBy(() -> mapPinService.getPins(
                contextId.toString(), 1288, studentId.toString(), "SCHOOL_STUDENT"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("WF-06 / DS-06a: Content Admin creates official ADMIN pin with GeoJSON LineString route")
    void createPin_ContentAdmin_CreatesAdminPinWithLineStringPath() {
        CreateMapPinRequest request = new CreateMapPinRequest();
        request.setLabel("Mũi tiến công của Trần Hưng Đạo");
        request.setDescription("Đại quân nhà Trần mai phục tại nhánh sông Chanh");
        request.setLatitude(20.945);
        request.setLongitude(106.815);
        request.setPinYear(1288);
        request.setPathGeoJson(Map.of(
                "type", "LineString",
                "coordinates", List.of(List.of(106.80, 20.93), List.of(106.815, 20.945))
        ));

        when(contextRepository.findById(contextId)).thenReturn(Optional.of(publishedContext));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));
        when(mapPinRepository.save(any(MapPin.class))).thenAnswer(inv -> {
            MapPin p = inv.getArgument(0);
            p.setPinId(UUID.randomUUID());
            return p;
        });

        MapPinResponse response = mapPinService.createPin(
                contextId.toString(), request, adminId.toString(), "CONTENT_ADMIN");

        assertThat(response.getPinOwnerType()).isEqualTo("ADMIN");
        assertThat(response.getLabel()).isEqualTo("Mũi tiến công của Trần Hưng Đạo");
        assertThat(response.getPinYear()).isEqualTo(1288);
        assertThat(response.getPathGeoJson()).isNotNull();
    }

    @Test
    @DisplayName("WF-06 / F-12: Student creates personal USER study pin on published context")
    void createPin_StudentOnPublishedContext_CreatesUserPin() {
        CreateMapPinRequest request = new CreateMapPinRequest();
        request.setLabel("Điểm chú ý ôn thi");
        request.setDescription("Nhớ mốc thời gian thủy triều rút");
        request.setLatitude(20.94);
        request.setLongitude(106.81);
        request.setPinYear(1288);

        when(contextRepository.findById(contextId)).thenReturn(Optional.of(publishedContext));
        when(userRepository.findById(studentId)).thenReturn(Optional.of(studentUser));
        when(mapPinRepository.save(any(MapPin.class))).thenAnswer(inv -> {
            MapPin p = inv.getArgument(0);
            p.setPinId(UUID.randomUUID());
            return p;
        });

        MapPinResponse response = mapPinService.createPin(
                contextId.toString(), request, studentId.toString(), "SCHOOL_STUDENT");

        assertThat(response.getPinOwnerType()).isEqualTo("USER");
        assertThat(response.getLabel()).isEqualTo("Điểm chú ý ôn thi");
    }

    @Test
    @DisplayName("WF-06: Invalid GeoJSON type (not LineString) is rejected with InvalidRequestException")
    void createPin_InvalidGeoJsonType_ThrowsInvalidRequestException() {
        CreateMapPinRequest request = new CreateMapPinRequest();
        request.setLabel("Pin lỗi GeoJSON");
        request.setLatitude(20.94);
        request.setLongitude(106.81);
        request.setPinYear(1288);
        request.setPathGeoJson(Map.of("type", "Polygon", "coordinates", List.of()));

        when(contextRepository.findById(contextId)).thenReturn(Optional.of(publishedContext));
        when(userRepository.findById(adminId)).thenReturn(Optional.of(adminUser));

        assertThatThrownBy(() -> mapPinService.createPin(
                contextId.toString(), request, adminId.toString(), "CONTENT_ADMIN"))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("LineString");
    }

    @Test
    @DisplayName("WF-06 / DS-06b: Non-admin student attempting to edit an official ADMIN pin is rejected")
    void updatePin_StudentAttemptsToEditAdminPin_ThrowsNotFound() {
        UUID pinId = UUID.randomUUID();
        MapPin officialPin = MapPin.builder()
                .pinId(pinId)
                .historicalContext(publishedContext)
                .createdBy(adminUser)
                .pinOwnerType("ADMIN")
                .label("Cứ điểm Đồi A1")
                .latitude(21.38)
                .longitude(103.02)
                .pinYear(1954)
                .build();

        UpdateMapPinRequest updateRequest = new UpdateMapPinRequest();
        updateRequest.setLabel("Sửa trái phép");

        when(mapPinRepository.findByPinIdAndDeletedAtIsNull(pinId)).thenReturn(Optional.of(officialPin));

        assertThatThrownBy(() -> mapPinService.updatePin(
                contextId.toString(), pinId.toString(), updateRequest, studentId.toString(), "SCHOOL_STUDENT"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(mapPinRepository, never()).save(any());
    }

    @Test
    @DisplayName("WF-06: Content Admin updates official ADMIN pin label and coordinates")
    void updatePin_AdminUpdatesOfficialPin_Success() {
        UUID pinId = UUID.randomUUID();
        MapPin officialPin = MapPin.builder()
                .pinId(pinId)
                .historicalContext(publishedContext)
                .createdBy(adminUser)
                .pinOwnerType("ADMIN")
                .label("Cứ điểm cũ")
                .latitude(21.38)
                .longitude(103.02)
                .pinYear(1954)
                .pathGeoJson("{\"type\":\"LineString\",\"coordinates\":[]}")
                .build();

        UpdateMapPinRequest updateRequest = new UpdateMapPinRequest();
        updateRequest.setLabel("Cứ điểm Đồi A1 - Điện Biên Phủ");
        updateRequest.setDescription("");

        when(mapPinRepository.findByPinIdAndDeletedAtIsNull(pinId)).thenReturn(Optional.of(officialPin));
        when(mapPinRepository.save(any(MapPin.class))).thenAnswer(inv -> inv.getArgument(0));

        MapPinResponse response = mapPinService.updatePin(
                contextId.toString(), pinId.toString(), updateRequest, adminId.toString(), "CONTENT_ADMIN");

        assertThat(response.getLabel()).isEqualTo("Cứ điểm Đồi A1 - Điện Biên Phủ");
        assertThat(response.getDescription()).isNull();
    }

    @Test
    @DisplayName("WF-06 / DS-06b: Non-admin student attempting to delete an official ADMIN pin is rejected")
    void deletePin_StudentAttemptsToDeleteAdminPin_ThrowsNotFound() {
        UUID pinId = UUID.randomUUID();
        MapPin officialPin = MapPin.builder()
                .pinId(pinId)
                .historicalContext(publishedContext)
                .createdBy(adminUser)
                .pinOwnerType("ADMIN")
                .label("Cứ điểm Đồi A1")
                .build();

        when(mapPinRepository.findByPinIdAndDeletedAtIsNull(pinId)).thenReturn(Optional.of(officialPin));

        assertThatThrownBy(() -> mapPinService.deletePin(
                contextId.toString(), pinId.toString(), studentId.toString(), "SCHOOL_STUDENT"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(mapPinRepository, never()).delete(any());
    }

    @Test
    @DisplayName("WF-06: Soft-deleting historical context cascades soft-delete to all map pins")
    void softDeleteAllPinsForContext_CascadesSoftDelete() {
        MapPin pin1 = MapPin.builder().pinId(UUID.randomUUID()).historicalContext(publishedContext).build();
        MapPin pin2 = MapPin.builder().pinId(UUID.randomUUID()).historicalContext(publishedContext).build();

        when(mapPinRepository.findByHistoricalContext_ContextIdAndDeletedAtIsNull(contextId))
                .thenReturn(List.of(pin1, pin2));

        mapPinService.softDeleteAllPinsForContext(contextId.toString());

        assertThat(pin1.getDeletedAt()).isNotNull();
        assertThat(pin2.getDeletedAt()).isNotNull();
        verify(mapPinRepository).saveAll(List.of(pin1, pin2));
    }
}
