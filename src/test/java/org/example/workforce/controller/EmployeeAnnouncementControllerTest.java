package org.example.workforce.controller;

import org.example.workforce.config.JwtUtil;
import org.example.workforce.model.Announcement;
import org.example.workforce.service.AnnouncementService;
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

@WebMvcTest(EmployeeAnnouncementController.class)
class EmployeeAnnouncementControllerTest {

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

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        when(ipAccessControlService.isIpAllowed(anyString())).thenReturn(true);
    }

    @Test
    @WithMockUser
    void testGetActiveAnnouncements() throws Exception {
        Page<Announcement> page = new PageImpl<>(Collections.emptyList());
        when(announcementService.getActiveAnnouncements(any())).thenReturn(page);

        mockMvc.perform(get("/api/employees/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser
    void testGetAnnouncement() throws Exception {
        Announcement announcement = Announcement.builder().announcementId(1).title("Test").build();
        when(announcementService.getAnnouncementById(1)).thenReturn(announcement);

        mockMvc.perform(get("/api/employees/announcements/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

