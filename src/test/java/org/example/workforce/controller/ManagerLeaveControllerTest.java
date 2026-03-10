package org.example.workforce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.LeaveActionRequest;
import org.example.workforce.model.LeaveApplication;
import org.example.workforce.model.enums.LeaveStatus;
import org.example.workforce.service.IpAccessControlService;
import org.example.workforce.service.LeaveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ManagerLeaveController.class)
class ManagerLeaveControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private LeaveService leaveService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private IpAccessControlService ipAccessControlService;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        when(ipAccessControlService.isIpAllowed(anyString())).thenReturn(true);
    }

    @Test
    @WithMockUser(username = "mgr@test.com", roles = "MANAGER")
    void testGetTeamLeaves() throws Exception {
        Page<LeaveApplication> page = new PageImpl<>(Collections.emptyList());
        when(leaveService.getTeamLeaves(eq("mgr@test.com"), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/manager/leaves/team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "mgr@test.com", roles = "MANAGER")
    void testActionLeave() throws Exception {
        LeaveApplication leave = LeaveApplication.builder().leaveId(1).status(LeaveStatus.APPROVED).build();
        when(leaveService.actionLeave(eq("mgr@test.com"), eq(1), any(LeaveActionRequest.class))).thenReturn(leave);

        LeaveActionRequest request = new LeaveActionRequest();
        request.setAction("APPROVED");
        request.setComments("Approved");

        mockMvc.perform(patch("/api/manager/leaves/1/action").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "mgr@test.com", roles = "MANAGER")
    void testGetTeamLeaveCalendar() throws Exception {
        when(leaveService.getTeamLeaveCalendar(eq("mgr@test.com"), any(), any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/manager/leaves/team/calendar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "mgr@test.com", roles = "MANAGER")
    void testGetTeamMemberBalance() throws Exception {
        when(leaveService.getTeamMemberBalance("mgr@test.com", "EMP001")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/manager/leaves/team/EMP001/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

