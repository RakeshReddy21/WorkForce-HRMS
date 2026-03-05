package org.example.workforce.service;

import org.example.workforce.dto.ChatMessageRequest;
import org.example.workforce.dto.ChatMessageResponse;
import org.example.workforce.dto.ConversationResponse;
import org.example.workforce.exception.AccessDeniedException;
import org.example.workforce.exception.ResourceNotFoundException;
import org.example.workforce.model.ChatConversation;
import org.example.workforce.model.ChatMessage;
import org.example.workforce.model.Employee;
import org.example.workforce.model.enums.MessageType;
import org.example.workforce.model.enums.Role;
import org.example.workforce.repository.ChatConversationRepository;
import org.example.workforce.repository.ChatMessageRepository;
import org.example.workforce.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ChatConversationRepository conversationRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private PresenceService presenceService;

    @Mock
    private WebSocketNotificationService wsNotificationService;

    @InjectMocks
    private ChatService chatService;

    private Employee user1;
    private Employee user2;
    private ChatConversation conversation;

    @BeforeEach
    void setUp() {
        user1 = Employee.builder()
                .employeeId(1)
                .email("user1@test.com")
                .firstName("John")
                .lastName("Doe")
                .employeeCode("EMP001")
                .role(Role.EMPLOYEE)
                .isActive(true)
                .build();

        user2 = Employee.builder()
                .employeeId(2)
                .email("user2@test.com")
                .firstName("Jane")
                .lastName("Smith")
                .employeeCode("EMP002")
                .role(Role.EMPLOYEE)
                .isActive(true)
                .build();

        conversation = ChatConversation.builder()
                .conversationId(1L)
                .participant1(user1)
                .participant2(user2)
                .lastMessageText("Hello!")
                .lastSenderId(1)
                .lastMessageAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getOrCreateConversation_ExistingConversation_Success() {
        when(employeeRepository.findByEmail("user1@test.com")).thenReturn(Optional.of(user1));
        when(employeeRepository.findById(2)).thenReturn(Optional.of(user2));
        when(conversationRepository.findByParticipants(1, 2)).thenReturn(Optional.of(conversation));
        when(messageRepository.countByConversation_ConversationIdAndIsReadAndSender_EmployeeIdNot(1L, false, 1))
                .thenReturn(0L);
        when(presenceService.isOnline("user2@test.com")).thenReturn(true);

        ConversationResponse result = chatService.getOrCreateConversation("user1@test.com", 2);

        assertNotNull(result);
        assertEquals(1L, result.getConversationId());
        assertEquals(2, result.getOtherParticipantId());
        assertTrue(result.isOnline());
        verify(conversationRepository, never()).save(any());
    }

    @Test
    void getOrCreateConversation_NewConversation_Success() {
        when(employeeRepository.findByEmail("user1@test.com")).thenReturn(Optional.of(user1));
        when(employeeRepository.findById(2)).thenReturn(Optional.of(user2));
        when(conversationRepository.findByParticipants(1, 2)).thenReturn(Optional.empty());
        when(conversationRepository.save(any(ChatConversation.class))).thenReturn(conversation);
        when(messageRepository.countByConversation_ConversationIdAndIsReadAndSender_EmployeeIdNot(anyLong(), anyBoolean(), anyInt()))
                .thenReturn(0L);
        when(presenceService.isOnline("user2@test.com")).thenReturn(false);

        ConversationResponse result = chatService.getOrCreateConversation("user1@test.com", 2);

        assertNotNull(result);
        assertFalse(result.isOnline());
    }

    @Test
    void getOrCreateConversation_WithYourself_ThrowsException() {
        when(employeeRepository.findByEmail("user1@test.com")).thenReturn(Optional.of(user1));
        when(employeeRepository.findById(1)).thenReturn(Optional.of(user1));

        assertThrows(AccessDeniedException.class,
                () -> chatService.getOrCreateConversation("user1@test.com", 1));
    }

    @Test
    void getOrCreateConversation_OtherEmployeeNotFound_ThrowsException() {
        when(employeeRepository.findByEmail("user1@test.com")).thenReturn(Optional.of(user1));
        when(employeeRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> chatService.getOrCreateConversation("user1@test.com", 99));
    }

    @Test
    void getMyConversations_Success() {
        when(employeeRepository.findByEmail("user1@test.com")).thenReturn(Optional.of(user1));
        when(conversationRepository.findAllByParticipant(1)).thenReturn(List.of(conversation));
        when(messageRepository.countByConversation_ConversationIdAndIsReadAndSender_EmployeeIdNot(1L, false, 1))
                .thenReturn(2L);
        when(presenceService.isOnline("user2@test.com")).thenReturn(true);

        List<ConversationResponse> result = chatService.getMyConversations("user1@test.com");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(2L, result.get(0).getUnreadCount());
    }

    @Test
    void getMyConversations_NoConversations() {
        when(employeeRepository.findByEmail("user1@test.com")).thenReturn(Optional.of(user1));
        when(conversationRepository.findAllByParticipant(1)).thenReturn(List.of());

        List<ConversationResponse> result = chatService.getMyConversations("user1@test.com");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getMessages_Success() {
        ChatMessage message = ChatMessage.builder()
                .messageId(1L)
                .conversation(conversation)
                .sender(user1)
                .content("Hello!")
                .messageType(MessageType.TEXT)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        when(employeeRepository.findByEmail("user1@test.com")).thenReturn(Optional.of(user1));
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        Pageable pageable = PageRequest.of(0, 20);
        when(messageRepository.findByConversation_ConversationIdOrderByCreatedAtDesc(1L, pageable))
                .thenReturn(new PageImpl<>(List.of(message)));

        Page<ChatMessageResponse> result = chatService.getMessages("user1@test.com", 1L, 0, 20);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getMessages_NotParticipant_ThrowsException() {
        Employee user3 = Employee.builder().employeeId(3).email("user3@test.com").build();
        when(employeeRepository.findByEmail("user3@test.com")).thenReturn(Optional.of(user3));
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        assertThrows(AccessDeniedException.class,
                () -> chatService.getMessages("user3@test.com", 1L, 0, 20));
    }

    @Test
    void getMessages_ConversationNotFound_ThrowsException() {
        when(employeeRepository.findByEmail("user1@test.com")).thenReturn(Optional.of(user1));
        when(conversationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> chatService.getMessages("user1@test.com", 99L, 0, 20));
    }

    @Test
    void sendMessage_WithConversationId_Success() {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setConversationId(1L);
        request.setContent("Hello World");
        request.setMessageType("TEXT");

        ChatMessage savedMsg = ChatMessage.builder()
                .messageId(1L).conversation(conversation).sender(user1)
                .content("Hello World").messageType(MessageType.TEXT)
                .isRead(false).createdAt(LocalDateTime.now()).build();

        when(employeeRepository.findByEmail("user1@test.com")).thenReturn(Optional.of(user1));
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(ChatMessage.class))).thenReturn(savedMsg);
        when(conversationRepository.save(any(ChatConversation.class))).thenReturn(conversation);
        when(messageRepository.countTotalUnreadForUser(2)).thenReturn(1L);
        doNothing().when(wsNotificationService).sendChatMessage(anyString(), any());
        doNothing().when(wsNotificationService).sendUnreadChatCount(anyString(), anyLong());

        ChatMessageResponse result = chatService.sendMessage("user1@test.com", request);

        assertNotNull(result);
        assertEquals("Hello World", result.getContent());
        verify(wsNotificationService).sendChatMessage(eq("user2@test.com"), any());
        verify(wsNotificationService).sendUnreadChatCount(eq("user2@test.com"), eq(1L));
    }

    @Test
    void sendMessage_WithRecipientId_Success() {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setRecipientId(2);
        request.setContent("New message");

        ChatMessage savedMsg = ChatMessage.builder()
                .messageId(1L).conversation(conversation).sender(user1)
                .content("New message").messageType(MessageType.TEXT)
                .isRead(false).createdAt(LocalDateTime.now()).build();

        when(employeeRepository.findByEmail("user1@test.com")).thenReturn(Optional.of(user1));
        when(employeeRepository.findById(2)).thenReturn(Optional.of(user2));
        when(conversationRepository.findByParticipants(1, 2)).thenReturn(Optional.of(conversation));
        when(messageRepository.save(any(ChatMessage.class))).thenReturn(savedMsg);
        when(conversationRepository.save(any(ChatConversation.class))).thenReturn(conversation);
        when(messageRepository.countTotalUnreadForUser(2)).thenReturn(1L);
        doNothing().when(wsNotificationService).sendChatMessage(anyString(), any());
        doNothing().when(wsNotificationService).sendUnreadChatCount(anyString(), anyLong());

        ChatMessageResponse result = chatService.sendMessage("user1@test.com", request);

        assertNotNull(result);
        assertEquals("New message", result.getContent());
    }

    @Test
    void sendMessage_NoConversationOrRecipient_ThrowsException() {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setContent("Test");

        when(employeeRepository.findByEmail("user1@test.com")).thenReturn(Optional.of(user1));

        assertThrows(AccessDeniedException.class,
                () -> chatService.sendMessage("user1@test.com", request));
    }

    @Test
    void sendMessage_NotParticipant_ThrowsException() {
        Employee user3 = Employee.builder().employeeId(3).email("user3@test.com").build();
        ChatMessageRequest request = new ChatMessageRequest();
        request.setConversationId(1L);
        request.setContent("Test");

        when(employeeRepository.findByEmail("user3@test.com")).thenReturn(Optional.of(user3));
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        assertThrows(AccessDeniedException.class,
                () -> chatService.sendMessage("user3@test.com", request));
    }

    @Test
    void markConversationAsRead_Success() {
        when(employeeRepository.findByEmail("user1@test.com")).thenReturn(Optional.of(user1));
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        when(messageRepository.markConversationAsRead(1L, 1)).thenReturn(3);
        when(messageRepository.countTotalUnreadForUser(1)).thenReturn(0L);
        doNothing().when(wsNotificationService).sendUnreadChatCount(anyString(), anyLong());

        int result = chatService.markConversationAsRead("user1@test.com", 1L);

        assertEquals(3, result);
        verify(wsNotificationService).sendUnreadChatCount("user1@test.com", 0L);
    }

    @Test
    void markConversationAsRead_NoMessagesToMark() {
        when(employeeRepository.findByEmail("user1@test.com")).thenReturn(Optional.of(user1));
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));
        when(messageRepository.markConversationAsRead(1L, 1)).thenReturn(0);

        int result = chatService.markConversationAsRead("user1@test.com", 1L);

        assertEquals(0, result);
        verify(wsNotificationService, never()).sendUnreadChatCount(anyString(), anyLong());
    }

    @Test
    void getTotalUnreadCount_Success() {
        when(employeeRepository.findByEmail("user1@test.com")).thenReturn(Optional.of(user1));
        when(messageRepository.countTotalUnreadForUser(1)).thenReturn(5L);

        long result = chatService.getTotalUnreadCount("user1@test.com");

        assertEquals(5L, result);
    }

    @Test
    void getOtherParticipantByConversation_Success() {
        when(conversationRepository.findById(1L)).thenReturn(Optional.of(conversation));

        Employee result = chatService.getOtherParticipantByConversation(1L, 1);

        assertNotNull(result);
        assertEquals(2, result.getEmployeeId());
    }

    @Test
    void getOtherParticipantByConversation_ConversationNotFound_ThrowsException() {
        when(conversationRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> chatService.getOtherParticipantByConversation(99L, 1));
    }
}
