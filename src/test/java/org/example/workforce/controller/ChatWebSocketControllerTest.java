package org.example.workforce.controller;

import org.example.workforce.dto.ChatMessageRequest;
import org.example.workforce.dto.TypingIndicator;
import org.example.workforce.model.Employee;
import org.example.workforce.model.enums.Role;
import org.example.workforce.repository.EmployeeRepository;
import org.example.workforce.service.ChatService;
import org.example.workforce.service.WebSocketNotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.Principal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatWebSocketControllerTest {

    @Mock
    private ChatService chatService;

    @Mock
    private WebSocketNotificationService wsNotificationService;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private Principal principal;

    @InjectMocks
    private ChatWebSocketController chatWebSocketController;

    private Employee sender;
    private Employee recipient;

    @BeforeEach
    void setUp() {
        sender = Employee.builder()
                .employeeId(1)
                .email("sender@example.com")
                .employeeCode("EMP001")
                .firstName("John")
                .lastName("Doe")
                .role(Role.EMPLOYEE)
                .isActive(true)
                .build();

        recipient = Employee.builder()
                .employeeId(2)
                .email("recipient@example.com")
                .employeeCode("EMP002")
                .firstName("Jane")
                .lastName("Smith")
                .role(Role.EMPLOYEE)
                .isActive(true)
                .build();
    }

    @Test
    void testSendMessage() {
        when(principal.getName()).thenReturn("sender@example.com");
        ChatMessageRequest request = new ChatMessageRequest();
        request.setConversationId(1L);
        request.setContent("Test message");

        chatWebSocketController.sendMessage(request, principal);

        verify(chatService, times(1)).sendMessage("sender@example.com", request);
    }

    @Test
    void testTypingIndicator() {
        when(principal.getName()).thenReturn("sender@example.com");
        when(employeeRepository.findByEmail("sender@example.com")).thenReturn(Optional.of(sender));
        when(chatService.getOtherParticipantByConversation(anyLong(), anyInt())).thenReturn(recipient);

        TypingIndicator indicator = new TypingIndicator();
        indicator.setConversationId(1L);
        indicator.setTyping(true);

        chatWebSocketController.typingIndicator(indicator, principal);

        verify(employeeRepository, times(1)).findByEmail("sender@example.com");
        verify(chatService, times(1)).getOtherParticipantByConversation(1L, 1);
        verify(wsNotificationService, times(1)).sendTypingIndicator("recipient@example.com", indicator);
    }

    @Test
    void testTypingIndicator_EmployeeNotFound() {
        when(principal.getName()).thenReturn("sender@example.com");
        when(employeeRepository.findByEmail("sender@example.com")).thenReturn(Optional.empty());

        TypingIndicator indicator = new TypingIndicator();
        indicator.setConversationId(1L);

        chatWebSocketController.typingIndicator(indicator, principal);

        verify(employeeRepository, times(1)).findByEmail("sender@example.com");
        verify(chatService, never()).getOtherParticipantByConversation(anyLong(), anyInt());
        verify(wsNotificationService, never()).sendTypingIndicator(anyString(), any());
    }
}

