package org.example.workforce.controller;

import org.example.workforce.config.JwtUtil;
import org.example.workforce.model.Notification;
import org.example.workforce.service.IpAccessControlService;
import org.example.workforce.service.NotificationService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private NotificationService notificationService;
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
    @WithMockUser(username = "emp@test.com")
    void testGetMyNotifications() throws Exception {
        Page<Notification> page = new PageImpl<>(Collections.emptyList());
        when(notificationService.getMyNotifications(eq("emp@test.com"), any(), any())).thenReturn(page);

        mockMvc.perform(get("/api/employees/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testGetUnreadCount() throws Exception {
        when(notificationService.getUnreadCount("emp@test.com")).thenReturn(3L);

        mockMvc.perform(get("/api/employees/notifications/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testMarkAsRead() throws Exception {
        Notification notification = Notification.builder().notificationId(1).build();
        when(notificationService.markAsRead("emp@test.com", 1)).thenReturn(notification);

        mockMvc.perform(patch("/api/employees/notifications/1/read").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testMarkAllAsRead() throws Exception {
        when(notificationService.markAllAsRead("emp@test.com")).thenReturn(5);

        mockMvc.perform(patch("/api/employees/notifications/read-all").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}

