package org.example.workforce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.IpRangeRequest;
import org.example.workforce.model.AllowedIpRange;
import org.example.workforce.service.IpAccessControlService;
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

@WebMvcTest(AdminIpAccessController.class)
class AdminIpAccessControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private IpAccessControlService ipAccessControlService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserDetailsService userDetailsService;

    private ObjectMapper objectMapper;
    private AllowedIpRange ipRange;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        ipRange = AllowedIpRange.builder()
                .ipRangeId(1)
                .ipRange("192.168.1.0/24")
                .description("Office")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetAllIpRanges() throws Exception {
        when(ipAccessControlService.getAllIpRanges()).thenReturn(List.of(ipRange));

        mockMvc.perform(get("/api/admin/ip-access"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testAddIpRange() throws Exception {
        when(ipAccessControlService.addIpRange(any(IpRangeRequest.class))).thenReturn(ipRange);

        IpRangeRequest request = new IpRangeRequest();
        request.setIpRange("192.168.1.0/24");
        request.setDescription("Office");

        mockMvc.perform(post("/api/admin/ip-access").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testUpdateIpRange() throws Exception {
        when(ipAccessControlService.updateIpRange(eq(1), any(IpRangeRequest.class))).thenReturn(ipRange);

        IpRangeRequest request = new IpRangeRequest();
        request.setIpRange("10.0.0.0/8");
        request.setDescription("VPN");

        mockMvc.perform(put("/api/admin/ip-access/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testToggleIpRange() throws Exception {
        when(ipAccessControlService.toggleIpRange(1)).thenReturn(ipRange);

        mockMvc.perform(patch("/api/admin/ip-access/1/toggle").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeleteIpRange() throws Exception {
        doNothing().when(ipAccessControlService).deleteIpRange(1);

        mockMvc.perform(delete("/api/admin/ip-access/1").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCheckIp() throws Exception {
        when(ipAccessControlService.isIpAllowed("192.168.1.100")).thenReturn(true);

        mockMvc.perform(get("/api/admin/ip-access/check").param("ip", "192.168.1.100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}





