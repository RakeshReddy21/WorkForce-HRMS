package org.example.workforce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.LeaveApplyRequest;
import org.example.workforce.model.LeaveApplication;
import org.example.workforce.model.LeaveBalance;
import org.example.workforce.model.enums.LeaveStatus;
import org.example.workforce.service.IpAccessControlService;
import org.example.workforce.service.LeaveService;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmployeeLeaveController.class)
class EmployeeLeaveControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LeaveService leaveService;

    @MockitoBean
    private IpAccessControlService ipAccessControlService;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserDetailsService userDetailsService;

    private ObjectMapper objectMapper;

    private LeaveApplyRequest leaveApplyRequest;
    private LeaveApplication leaveApplication;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        
        leaveApplyRequest = new LeaveApplyRequest();
        leaveApplyRequest.setLeaveTypeId(1);
        leaveApplyRequest.setStartDate(LocalDate.now().plusDays(1));
        leaveApplyRequest.setEndDate(LocalDate.now().plusDays(3));
        leaveApplyRequest.setReason("Personal work");

        leaveApplication = LeaveApplication.builder()
                .leaveId(1)
                .status(LeaveStatus.PENDING)
                .build();

        // Mock IP access control to allow all IPs for testing
        when(ipAccessControlService.isIpAllowed(anyString())).thenReturn(true);
    }

    @Test
    @WithMockUser(username = "employee@test.com")
    void testApplyLeave_Success() throws Exception {

        when(leaveService.applyLeave(anyString(), any(LeaveApplyRequest.class)))
                .thenReturn(leaveApplication);

        mockMvc.perform(post("/api/employees/leaves/apply")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(leaveApplyRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.leaveId").value(1));
    }

    @Test
    @WithMockUser(username = "employee@test.com")
    void testGetMyLeaves_Success() throws Exception {

        Page<LeaveApplication> page = new PageImpl<>(Collections.singletonList(leaveApplication));
        when(leaveService.getMyLeaves(anyString(), any(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/employees/leaves")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @WithMockUser(username = "employee@test.com")
    void testGetMyLeaveBalance_Success() throws Exception {

        LeaveBalance balance = LeaveBalance.builder()
                .balanceId(1)
                .totalLeaves(10)
                .usedLeaves(0)
                .build();
        when(leaveService.getMyLeaveBalance(anyString()))
                .thenReturn(Collections.singletonList(balance));

        mockMvc.perform(get("/api/employees/leaves/balance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @WithMockUser(username = "employee@test.com")
    void testCancelLeave_Success() throws Exception {

        leaveApplication.setStatus(LeaveStatus.CANCELLED);
        when(leaveService.cancelLeave(anyString(), anyInt()))
                .thenReturn(leaveApplication);

        mockMvc.perform(patch("/api/employees/leaves/1/cancel")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }
}
