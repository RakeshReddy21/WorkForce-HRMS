package org.example.workforce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.DesignationRequest;
import org.example.workforce.model.Designation;
import org.example.workforce.service.DesignationService;
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

@WebMvcTest(AdminDesignationController.class)
class AdminDesignationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private DesignationService designationService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private IpAccessControlService ipAccessControlService;

    private ObjectMapper objectMapper;
    private Designation designation;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        designation = Designation.builder().designationId(1).designationName("Senior Dev").isActive(true).build();
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testCreateDesignation() throws Exception {
        when(designationService.createDesignation(any(DesignationRequest.class))).thenReturn(designation);
        DesignationRequest request = new DesignationRequest();
        request.setDesignationName("Senior Dev");

        mockMvc.perform(post("/api/admin/designations").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testUpdateDesignation() throws Exception {
        when(designationService.updateDesignation(eq(1), any(DesignationRequest.class))).thenReturn(designation);
        DesignationRequest request = new DesignationRequest();
        request.setDesignationName("Lead Dev");

        mockMvc.perform(put("/api/admin/designations/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeactivateDesignation() throws Exception {
        when(designationService.deactivateDesignation(1)).thenReturn(designation);

        mockMvc.perform(patch("/api/admin/designations/1/deactivate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testActivateDesignation() throws Exception {
        when(designationService.activateDesignation(1)).thenReturn(designation);

        mockMvc.perform(patch("/api/admin/designations/1/activate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetAllDesignations() throws Exception {
        when(designationService.getAllDesignations()).thenReturn(List.of(designation));

        mockMvc.perform(get("/api/admin/designations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetDesignation() throws Exception {
        when(designationService.getDesignationById(1)).thenReturn(designation);

        mockMvc.perform(get("/api/admin/designations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}





