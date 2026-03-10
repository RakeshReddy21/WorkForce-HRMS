package org.example.workforce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.DepartmentRequest;
import org.example.workforce.model.Department;
import org.example.workforce.service.DepartmentService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminDepartmentController.class)
class AdminDepartmentControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private DepartmentService departmentService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private IpAccessControlService ipAccessControlService;

    private ObjectMapper objectMapper;
    private Department department;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        department = Department.builder().departmentId(1).departmentName("IT").isActive(true).build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreateDepartment() throws Exception {
        when(departmentService.createDepartment(any(DepartmentRequest.class))).thenReturn(department);
        DepartmentRequest request = new DepartmentRequest();
        request.setDepartmentName("IT");

        mockMvc.perform(post("/api/admin/departments").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Department created successfully"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testUpdateDepartment() throws Exception {
        when(departmentService.updateDepartment(eq(1), any(DepartmentRequest.class))).thenReturn(department);
        DepartmentRequest request = new DepartmentRequest();
        request.setDepartmentName("IT Updated");

        mockMvc.perform(put("/api/admin/departments/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeactivateDepartment() throws Exception {
        department.setIsActive(false);
        when(departmentService.deactivateDepartment(1)).thenReturn(department);

        mockMvc.perform(patch("/api/admin/departments/1/deactivate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Department deactivated successfully"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testActivateDepartment() throws Exception {
        when(departmentService.activateDepartment(1)).thenReturn(department);

        mockMvc.perform(patch("/api/admin/departments/1/activate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Department activated successfully"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetAllDepartments() throws Exception {
        when(departmentService.getAllDepartments()).thenReturn(List.of(department));

        mockMvc.perform(get("/api/admin/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Departments fetched successfully"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetDepartment() throws Exception {
        when(departmentService.getDepartmentById(1)).thenReturn(department);

        mockMvc.perform(get("/api/admin/departments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Department fetched successfully"));
    }
}





