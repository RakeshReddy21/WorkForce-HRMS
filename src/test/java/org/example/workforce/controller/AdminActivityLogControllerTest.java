package org.example.workforce.controller;

import org.example.workforce.config.JwtUtil;
import org.example.workforce.model.ActivityLog;
import org.example.workforce.service.ActivityLogService;
import org.example.workforce.service.IpAccessControlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminActivityLogController.class)
class AdminActivityLogControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ActivityLogService activityLogService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private IpAccessControlService ipAccessControlService;

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetAllLogs() throws Exception {
        Page<ActivityLog> page = new PageImpl<>(Collections.emptyList());
        when(activityLogService.getAllLogs(any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/activity-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetLogsByEntityType() throws Exception {
        Page<ActivityLog> page = new PageImpl<>(Collections.emptyList());
        when(activityLogService.getLogsByEntityType(eq("EMPLOYEE"), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/activity-logs/entity-type/EMPLOYEE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetLogsByEmployee() throws Exception {
        Page<ActivityLog> page = new PageImpl<>(Collections.emptyList());
        when(activityLogService.getLogsByEmployee(eq(1), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/activity-logs/employee/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetLogsByEntity() throws Exception {
        when(activityLogService.getLogsByEntity("EMPLOYEE", 1)).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/activity-logs/entity/EMPLOYEE/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetLogsByDateRange() throws Exception {
        Page<ActivityLog> page = new PageImpl<>(Collections.emptyList());
        when(activityLogService.getLogsByDateRange(any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/activity-logs/date-range")
                        .param("startDate", "2026-01-01")
                        .param("endDate", "2026-03-05"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}





