package org.example.workforce.controller;

import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.ChatMessageResponse;
import org.example.workforce.dto.ConversationResponse;
import org.example.workforce.service.ChatService;
import org.example.workforce.service.IpAccessControlService;
import org.example.workforce.service.PresenceService;
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
import java.util.HashSet;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private ChatService chatService;
    @MockitoBean
    private PresenceService presenceService;
    @MockitoBean
    private JwtUtil jwtUtil;
    @MockitoBean
    private UserDetailsService userDetailsService;
    @MockitoBean
    private IpAccessControlService ipAccessControlService;

    @Test
    @WithMockUser(username = "emp@test.com")
    void testGetMyConversations() throws Exception {
        when(chatService.getMyConversations("emp@test.com")).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/chat/conversations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testGetOrCreateConversation() throws Exception {
        ConversationResponse response = new ConversationResponse();
        when(chatService.getOrCreateConversation("emp@test.com", 2)).thenReturn(response);

        mockMvc.perform(post("/api/chat/conversations/2").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testGetMessages() throws Exception {
        Page<ChatMessageResponse> page = new PageImpl<>(Collections.emptyList());
        when(chatService.getMessages(eq("emp@test.com"), eq(1L), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/chat/conversations/1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testMarkConversationAsRead() throws Exception {
        when(chatService.markConversationAsRead("emp@test.com", 1L)).thenReturn(3);

        mockMvc.perform(patch("/api/chat/conversations/1/read").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "emp@test.com")
    void testGetUnreadCount() throws Exception {
        when(chatService.getTotalUnreadCount("emp@test.com")).thenReturn(5L);

        mockMvc.perform(get("/api/chat/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser
    void testGetOnlineUsers() throws Exception {
        Set<String> onlineUsers = new HashSet<>(Set.of("user1@test.com"));
        when(presenceService.getOnlineUsers()).thenReturn(onlineUsers);

        mockMvc.perform(get("/api/chat/online-users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}


















