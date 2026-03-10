package org.example.workforce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.EmployeeProfileResponse;
import org.example.workforce.dto.RegisterEmployeeRequest;
import org.example.workforce.dto.UpdateEmployeeRequest;
import org.example.workforce.model.Employee;
import org.example.workforce.service.EmployeeService;
import org.example.workforce.service.IpAccessControlService;
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
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminEmployeeController.class)
class AdminEmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private EmployeeService employeeService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private IpAccessControlService ipAccessControlService;

    private ObjectMapper objectMapper;
    private EmployeeProfileResponse profileResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        profileResponse = new EmployeeProfileResponse();
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testRegisterEmployee() throws Exception {
        Employee employee = Employee.builder().employeeId(1).employeeCode("EMP001").build();
        when(employeeService.registerEmployee(any(RegisterEmployeeRequest.class))).thenReturn(employee);
        when(employeeService.getEmployeeByCode("EMP001")).thenReturn(profileResponse);

        RegisterEmployeeRequest request = new RegisterEmployeeRequest();
        request.setEmail("new@test.com");
        request.setFirstName("Jane");
        request.setLastName("Doe");
        request.setPassword("Password@123");
        request.setJoiningDate(java.time.LocalDate.now());
        request.setDepartmentId(1);
        request.setDesignationId(1);
        request.setRole("EMPLOYEE");

        mockMvc.perform(post("/api/admin/employees/register")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetEmployee() throws Exception {
        when(employeeService.getEmployeeByCode("EMP001")).thenReturn(profileResponse);

        mockMvc.perform(get("/api/admin/employees/EMP001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Employee fetched successfully"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetAllEmployees() throws Exception {
        Page<EmployeeProfileResponse> page = new PageImpl<>(Collections.emptyList());
        when(employeeService.getEmployees(any(), any(), any(), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Employees fetched successfully"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testDeactivateEmployee() throws Exception {
        when(employeeService.deactivateEmployee(eq("EMP001"), anyString())).thenReturn(profileResponse);

        mockMvc.perform(patch("/api/admin/employees/EMP001/deactivate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Employee deactivated successfully"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testActivateEmployee() throws Exception {
        when(employeeService.activateEmployee(eq("EMP001"), anyString())).thenReturn(profileResponse);

        mockMvc.perform(patch("/api/admin/employees/EMP001/activate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Employee reactivated successfully"));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testEnable2FA() throws Exception {
        doNothing().when(employeeService).enable2FA(eq("EMP001"), anyString());

        mockMvc.perform(patch("/api/admin/employees/EMP001/enable-2fa").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testDisable2FA() throws Exception {
        doNothing().when(employeeService).disable2FA(eq("EMP001"), anyString());

        mockMvc.perform(patch("/api/admin/employees/EMP001/disable-2fa").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testForceEnable2FAForAll() throws Exception {
        when(employeeService.forceEnable2FAForAll(anyString())).thenReturn(5);

        mockMvc.perform(post("/api/admin/employees/force-enable-2fa").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

