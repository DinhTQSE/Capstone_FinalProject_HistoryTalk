package com.historytalk.controller.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.map.CreateMapPinRequest;
import com.historytalk.dto.map.MapPinResponse;
import com.historytalk.dto.map.UpdateMapPinRequest;
import com.historytalk.exception.GlobalExceptionHandler;
import com.historytalk.exception.ResourceNotFoundException;
import com.historytalk.service.map.MapPinService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MapPinControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MapPinService mapPinService;

    @InjectMocks
    private MapPinController mapPinController;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String contextId;
    private String pinId;

    @BeforeEach
    void setUp() {
        contextId = UUID.randomUUID().toString();
        pinId = UUID.randomUUID().toString();
        mockMvc = MockMvcBuilders.standaloneSetup(mapPinController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("IT-MAP-01: GET /api/v1/historical-contexts/{contextId}/map-pins?year=1288 returns 200 OK")
    void getPins_ValidYear_Returns200() throws Exception {
        MapPinResponse pin = MapPinResponse.builder()
                .pinId(pinId)
                .contextId(contextId)
                .pinOwnerType("ADMIN")
                .label("Bãi cọc Bạch Đằng")
                .latitude(20.9412)
                .longitude(106.8123)
                .pinYear(1288)
                .build();

        when(mapPinService.getPins(eq(contextId), eq(1288), any(), any())).thenReturn(List.of(pin));

        mockMvc.perform(get("/api/v1/historical-contexts/{contextId}/map-pins", contextId)
                        .param("year", "1288"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].pinId").value(pinId))
                .andExpect(jsonPath("$.data[0].label").value("Bãi cọc Bạch Đằng"))
                .andExpect(jsonPath("$.data[0].pinYear").value(1288));
    }

    @Test
    @DisplayName("IT-MAP-02: POST /api/v1/historical-contexts/{contextId}/map-pins creates pin and returns 201 Created")
    void createPin_ValidRequest_Returns201() throws Exception {
        CreateMapPinRequest req = new CreateMapPinRequest();
        req.setLabel("Đồi A1");
        req.setDescription("Cứ điểm then chốt phía đông Mường Thanh");
        req.setLatitude(21.385);
        req.setLongitude(103.021);
        req.setPinYear(1954);

        MapPinResponse created = MapPinResponse.builder()
                .pinId(pinId)
                .contextId(contextId)
                .pinOwnerType("ADMIN")
                .label("Đồi A1")
                .latitude(21.385)
                .longitude(103.021)
                .pinYear(1954)
                .build();

        when(mapPinService.createPin(eq(contextId), any(CreateMapPinRequest.class), any(), any()))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/historical-contexts/{contextId}/map-pins", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pinId").value(pinId))
                .andExpect(jsonPath("$.data.label").value("Đồi A1"));
    }

    @Test
    @DisplayName("IT-MAP-03: POST /api/v1/historical-contexts/{contextId}/map-pins missing label returns 400 Bad Request")
    void createPin_MissingRequiredFields_Returns400() throws Exception {
        CreateMapPinRequest invalid = new CreateMapPinRequest();
        invalid.setLatitude(21.0);

        mockMvc.perform(post("/api/v1/historical-contexts/{contextId}/map-pins", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("IT-MAP-04: PUT /api/v1/historical-contexts/{contextId}/map-pins/{pinId} unauthorized edit of official pin returns 404")
    void updatePin_UnauthorizedEditOfOfficialPin_Returns404() throws Exception {
        UpdateMapPinRequest req = new UpdateMapPinRequest();
        req.setLabel("Chỉnh sửa trái phép");

        when(mapPinService.updatePin(eq(contextId), eq(pinId), any(UpdateMapPinRequest.class), any(), any()))
                .thenThrow(new ResourceNotFoundException("Không tìm thấy map pin với ID: " + pinId));

        mockMvc.perform(put("/api/v1/historical-contexts/{contextId}/map-pins/{pinId}", contextId, pinId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("IT-MAP-05: DELETE /api/v1/historical-contexts/{contextId}/map-pins/{pinId} returns 204 No Content")
    void deletePin_Authorized_Returns204() throws Exception {
        doNothing().when(mapPinService).deletePin(eq(contextId), eq(pinId), any(), any());

        mockMvc.perform(delete("/api/v1/historical-contexts/{contextId}/map-pins/{pinId}", contextId, pinId))
                .andExpect(status().isNoContent());
    }
}
