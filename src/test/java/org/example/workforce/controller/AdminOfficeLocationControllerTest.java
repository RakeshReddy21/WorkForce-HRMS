package org.example.workforce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.OfficeLocationRequest;
import org.example.workforce.model.OfficeLocation;
import org.example.workforce.service.IpAccessControlService;
import org.example.workforce.service.OfficeLocationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminOfficeLocationController.class)
class AdminOfficeLocationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private OfficeLocationService officeLocationService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private IpAccessControlService ipAccessControlService;

    private ObjectMapper objectMapper;
    private OfficeLocation location;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        location = OfficeLocation.builder()
                .locationId(1).locationName("HQ").address("123 Main St")
                .latitude(12.97).longitude(77.59).radiusMeters(500)
                .isActive(true).createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetAllLocations() throws Exception {
        when(officeLocationService.getAllLocations()).thenReturn(List.of(location));

        mockMvc.perform(get("/api/admin/office-locations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetLocation() throws Exception {
        when(officeLocationService.getLocationById(1)).thenReturn(location);

        mockMvc.perform(get("/api/admin/office-locations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testAddLocation() throws Exception {
        when(officeLocationService.addLocation(any(OfficeLocationRequest.class))).thenReturn(location);

        OfficeLocationRequest request = new OfficeLocationRequest();
        request.setLocationName("HQ");
        request.setAddress("123 Main St");
        request.setLatitude(12.97);
        request.setLongitude(77.59);
        request.setRadiusMeters(500);

        mockMvc.perform(post("/api/admin/office-locations").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testUpdateLocation() throws Exception {
        when(officeLocationService.updateLocation(eq(1), any(OfficeLocationRequest.class))).thenReturn(location);

        OfficeLocationRequest request = new OfficeLocationRequest();
        request.setLocationName("HQ Updated");
        request.setLatitude(12.97);
        request.setLongitude(77.59);

        mockMvc.perform(put("/api/admin/office-locations/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testToggleLocation() throws Exception {
        when(officeLocationService.toggleLocation(1)).thenReturn(location);

        mockMvc.perform(patch("/api/admin/office-locations/1/toggle").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeleteLocation() throws Exception {
        doNothing().when(officeLocationService).deleteLocation(1);

        mockMvc.perform(delete("/api/admin/office-locations/1").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetActiveLocations() throws Exception {
        when(officeLocationService.getActiveLocations()).thenReturn(List.of(location));

        mockMvc.perform(get("/api/admin/office-locations/active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

