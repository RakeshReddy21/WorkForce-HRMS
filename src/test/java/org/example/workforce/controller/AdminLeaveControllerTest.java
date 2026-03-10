package org.example.workforce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.HolidayRequest;
import org.example.workforce.dto.LeaveTypeRequest;
import org.example.workforce.model.Holiday;
import org.example.workforce.model.LeaveApplication;
import org.example.workforce.model.LeaveBalance;
import org.example.workforce.model.LeaveType;
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

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminLeaveController.class)
class AdminLeaveControllerTest {

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
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreateLeaveType() throws Exception {
        LeaveType leaveType = LeaveType.builder().leaveTypeId(1).leaveTypeName("Casual").defaultDays(10).build();
        when(leaveService.createLeaveType(any(LeaveTypeRequest.class))).thenReturn(leaveType);

        LeaveTypeRequest request = new LeaveTypeRequest();
        request.setLeaveTypeName("Casual");
        request.setDefaultDays(10);

        mockMvc.perform(post("/api/admin/leaves/types").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetAllLeaveTypes() throws Exception {
        when(leaveService.getAllLeaveType()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/leaves/types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testUpdateLeaveType() throws Exception {
        LeaveType leaveType = LeaveType.builder().leaveTypeId(1).leaveTypeName("Sick").defaultDays(12).build();
        when(leaveService.updateLeaveType(eq(1), any(LeaveTypeRequest.class))).thenReturn(leaveType);

        LeaveTypeRequest request = new LeaveTypeRequest();
        request.setLeaveTypeName("Sick");
        request.setDefaultDays(12);

        mockMvc.perform(put("/api/admin/leaves/types/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testGetEmployeeLeaveBalance() throws Exception {
        when(leaveService.getEmployeeBalance("EMP001")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/leaves/balance/EMP001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreateHoliday() throws Exception {
        Holiday holiday = Holiday.builder().holidayId(1).holidayName("New Year").build();
        when(leaveService.createHoliday(any(HolidayRequest.class))).thenReturn(holiday);

        HolidayRequest request = new HolidayRequest();
        request.setHolidayName("New Year");
        request.setHolidayDate(LocalDate.of(2026, 1, 1));

        mockMvc.perform(post("/api/admin/leaves/holidays").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeleteHoliday() throws Exception {
        doNothing().when(leaveService).deleteHoliday(1);

        mockMvc.perform(delete("/api/admin/leaves/holidays/1").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetAllLeaveApplications() throws Exception {
        Page<LeaveApplication> page = new PageImpl<>(Collections.emptyList());
        when(leaveService.getAllLeaveApplications(any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/leaves/applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetHolidays() throws Exception {
        when(leaveService.getHolidays(any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/admin/leaves/holidays"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

