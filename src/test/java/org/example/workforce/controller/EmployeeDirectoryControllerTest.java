package org.example.workforce.controller;

import org.example.workforce.config.JwtUtil;
import org.example.workforce.model.Employee;
import org.example.workforce.model.enums.Role;
import org.example.workforce.repository.EmployeeRepository;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmployeeDirectoryController.class)
class EmployeeDirectoryControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private EmployeeRepository employeeRepository;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private IpAccessControlService ipAccessControlService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        when(ipAccessControlService.isIpAllowed(anyString())).thenReturn(true);
    }

    @Test
    @WithMockUser
    void testSearchDirectory_NoParams() throws Exception {
        Page<Employee> page = new PageImpl<>(Collections.emptyList());
        when(employeeRepository.findByIsActive(eq(true), any())).thenReturn(page);

        mockMvc.perform(get("/api/employees/directory"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser
    void testSearchDirectory_WithKeyword() throws Exception {
        Employee emp = Employee.builder().employeeId(1).employeeCode("EMP001").firstName("John").lastName("Doe")
                .email("john@test.com").role(Role.EMPLOYEE).isActive(true).build();
        Page<Employee> page = new PageImpl<>(Collections.singletonList(emp));
        when(employeeRepository.searchByKeyword(eq("John"), any())).thenReturn(page);

        mockMvc.perform(get("/api/employees/directory").param("keyword", "John"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser
    void testSearchDirectory_WithDepartment() throws Exception {
        Page<Employee> page = new PageImpl<>(Collections.emptyList());
        when(employeeRepository.findByDepartment_DepartmentId(eq(1), any())).thenReturn(page);

        mockMvc.perform(get("/api/employees/directory").param("departmentId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

