package org.example.workforce.controller;

import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.EmployeeProfileResponse;
import org.example.workforce.model.Employee;
import org.example.workforce.model.enums.Role;
import org.example.workforce.repository.EmployeeRepository;
import org.example.workforce.service.EmployeeService;
import org.example.workforce.service.IpAccessControlService;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ManagerTeamController.class)
class ManagerTeamControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private EmployeeRepository employeeRepository;
    @MockitoBean
    private EmployeeService employeeService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private IpAccessControlService ipAccessControlService;

    private Employee manager;

    @BeforeEach
    void setUp() {
        when(ipAccessControlService.isIpAllowed(anyString())).thenReturn(true);
        manager = Employee.builder()
                .employeeId(1).employeeCode("MGR001").email("mgr@test.com")
                .firstName("Manager").lastName("Test").role(Role.MANAGER).isActive(true)
                .build();
    }

    @Test
    @WithMockUser(username = "mgr@test.com", roles = "MANAGER")
    void testGetTeamMembers() throws Exception {
        when(employeeRepository.findByEmail("mgr@test.com")).thenReturn(Optional.of(manager));
        Page<Employee> page = new PageImpl<>(Collections.emptyList());
        when(employeeRepository.findByManager_EmployeeCode(eq("MGR001"), any())).thenReturn(page);

        mockMvc.perform(get("/api/manager/team"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "mgr@test.com", roles = "MANAGER")
    void testGetTeamMemberProfile() throws Exception {
        Employee member = Employee.builder()
                .employeeId(2).employeeCode("EMP001").firstName("Emp").lastName("Test")
                .role(Role.EMPLOYEE).isActive(true).manager(manager).build();
        when(employeeRepository.findByEmail("mgr@test.com")).thenReturn(Optional.of(manager));
        when(employeeRepository.findByEmployeeCode("EMP001")).thenReturn(Optional.of(member));
        when(employeeService.getEmployeeByCode("EMP001")).thenReturn(new EmployeeProfileResponse());

        mockMvc.perform(get("/api/manager/team/EMP001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "mgr@test.com", roles = "MANAGER")
    void testGetTeamCount() throws Exception {
        Employee member = Employee.builder().employeeId(2).isActive(true).build();
        when(employeeRepository.findByEmail("mgr@test.com")).thenReturn(Optional.of(manager));
        when(employeeRepository.findByManager_EmployeeCode("MGR001")).thenReturn(List.of(member));

        mockMvc.perform(get("/api/manager/team/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

