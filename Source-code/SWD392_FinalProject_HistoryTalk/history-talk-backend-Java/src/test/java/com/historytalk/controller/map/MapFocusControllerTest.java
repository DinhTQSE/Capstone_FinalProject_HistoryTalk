package com.historytalk.controller.map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.historytalk.dto.map.CreateMapFocusRequest;
import com.historytalk.dto.map.MapFocusResponse;
import com.historytalk.dto.map.ReorderMapFocusRequest;
import com.historytalk.exception.GlobalExceptionHandler;
import com.historytalk.service.map.MapFocusService;
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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MapFocusControllerTest {

    private MockMvc mockMvc;

    @Mock
    private MapFocusService mapFocusService;

    @InjectMocks
    private MapFocusController mapFocusController;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String contextId;
    private String focusId;

    @BeforeEach
    void setUp() {
        contextId = UUID.randomUUID().toString();
        focusId = UUID.randomUUID().toString();
        mockMvc = MockMvcBuilders.standaloneSetup(mapFocusController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("IT-FOCUS-01: GET /api/v1/historical-contexts/{contextId}/map-focuses returns 200 OK")
    void getFocuses_Returns200() throws Exception {
        MapFocusResponse focus = MapFocusResponse.builder()
                .focusId(focusId)
                .contextId(contextId)
                .name("Sông Bạch Đằng")
                .latitude(20.9412)
                .longitude(106.8123)
                .zoomLevel(12.0)
                .orderIndex(0)
                .isDefault(true)
                .isActive(true)
                .build();

        when(mapFocusService.getFocuses(eq(contextId), any())).thenReturn(List.of(focus));

        mockMvc.perform(get("/api/v1/historical-contexts/{contextId}/map-focuses", contextId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].name").value("Sông Bạch Đằng"))
                .andExpect(jsonPath("$.data[0].zoomLevel").value(12.0));
    }

    @Test
    @DisplayName("IT-FOCUS-02: POST /api/v1/historical-contexts/{contextId}/map-focuses creates focus and returns 201")
    void createFocus_ValidRequest_Returns201() throws Exception {
        CreateMapFocusRequest req = new CreateMapFocusRequest();
        req.setName("Bến Bình Than");
        req.setLatitude(21.05);
        req.setLongitude(106.25);
        req.setZoomLevel(11.0);
        req.setIsDefault(true);

        MapFocusResponse created = MapFocusResponse.builder()
                .focusId(focusId)
                .contextId(contextId)
                .name("Bến Bình Than")
                .latitude(21.05)
                .longitude(106.25)
                .zoomLevel(11.0)
                .orderIndex(0)
                .isDefault(true)
                .build();

        when(mapFocusService.createFocus(eq(contextId), any(CreateMapFocusRequest.class), any(), any()))
                .thenReturn(created);

        mockMvc.perform(post("/api/v1/historical-contexts/{contextId}/map-focuses", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("Bến Bình Than"));
    }

    @Test
    @DisplayName("IT-FOCUS-03: PATCH /api/v1/historical-contexts/{contextId}/map-focuses/reorder returns 200")
    void reorderFocuses_ValidRequest_Returns200() throws Exception {
        ReorderMapFocusRequest req = new ReorderMapFocusRequest();
        req.setFocusIds(List.of(focusId));

        MapFocusResponse item = MapFocusResponse.builder()
                .focusId(focusId)
                .orderIndex(0)
                .build();

        when(mapFocusService.reorderFocuses(eq(contextId), any(ReorderMapFocusRequest.class), any()))
                .thenReturn(List.of(item));

        mockMvc.perform(patch("/api/v1/historical-contexts/{contextId}/map-focuses/reorder", contextId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].focusId").value(focusId));
    }

    @Test
    @DisplayName("IT-FOCUS-04: DELETE /api/v1/historical-contexts/{contextId}/map-focuses/{focusId} returns 204")
    void deleteFocus_Returns204() throws Exception {
        doNothing().when(mapFocusService).deleteFocus(eq(contextId), eq(focusId), any());

        mockMvc.perform(delete("/api/v1/historical-contexts/{contextId}/map-focuses/{focusId}", contextId, focusId))
                .andExpect(status().isNoContent());
    }
}
