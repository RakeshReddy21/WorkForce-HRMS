package org.example.workforce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.AnnouncementRequest;
import org.example.workforce.model.Announcement;
import org.example.workforce.service.AnnouncementService;
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
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminAnnouncementController.class)
class AdminAnnouncementControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AnnouncementService announcementService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private IpAccessControlService ipAccessControlService;

    private ObjectMapper objectMapper;
    private Announcement announcement;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        announcement = Announcement.builder().announcementId(1).title("Test").content("Content").isActive(true).build();
    }

    @Test
    @WithMockUser(username = "admin@test.com", roles = "ADMIN")
    void testCreateAnnouncement() throws Exception {
        when(announcementService.createAnnouncement(anyString(), any(AnnouncementRequest.class))).thenReturn(announcement);

        AnnouncementRequest request = new AnnouncementRequest();
        request.setTitle("Test");
        request.setContent("Content");

        mockMvc.perform(post("/api/admin/announcements").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testUpdateAnnouncement() throws Exception {
        when(announcementService.updateAnnouncement(eq(1), any(AnnouncementRequest.class))).thenReturn(announcement);

        AnnouncementRequest request = new AnnouncementRequest();
        request.setTitle("Updated");
        request.setContent("Updated Content");

        mockMvc.perform(put("/api/admin/announcements/1").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testDeactivateAnnouncement() throws Exception {
        when(announcementService.deactivateAnnouncement(1)).thenReturn(announcement);

        mockMvc.perform(patch("/api/admin/announcements/1/deactivate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testActivateAnnouncement() throws Exception {
        when(announcementService.activateAnnouncement(1)).thenReturn(announcement);

        mockMvc.perform(patch("/api/admin/announcements/1/activate").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void testGetAllAnnouncements() throws Exception {
        Page<Announcement> page = new PageImpl<>(Collections.emptyList());
        when(announcementService.getAllAnnouncements(any())).thenReturn(page);

        mockMvc.perform(get("/api/admin/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}





