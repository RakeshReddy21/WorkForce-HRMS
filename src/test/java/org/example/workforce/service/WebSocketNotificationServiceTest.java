package org.example.workforce.service;

import org.example.workforce.dto.ChatMessageResponse;
import org.example.workforce.dto.TypingIndicator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketNotificationServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private WebSocketNotificationService webSocketNotificationService;

    @Test
    void sendChatMessage_Success() {
        ChatMessageResponse message = ChatMessageResponse.builder()
                .messageId(1L)
                .conversationId(10L)
                .senderId(1)
                .senderName("John Doe")
                .recipientId(2)
                .content("Hello!")
                .messageType("TEXT")
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        webSocketNotificationService.sendChatMessage("recipient@test.com", message);

        verify(messagingTemplate).convertAndSendToUser(
                "recipient@test.com", "/queue/messages", message);
    }

    @Test
    void sendChatMessage_FileMessage() {
        ChatMessageResponse message = ChatMessageResponse.builder()
                .messageId(2L)
                .conversationId(10L)
                .senderId(1)
                .senderName("John Doe")
                .recipientId(2)
                .content("Check this file")
                .messageType("FILE")
                .fileUrl("/files/doc.pdf")
                .fileName("doc.pdf")
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();

        webSocketNotificationService.sendChatMessage("recipient@test.com", message);

        verify(messagingTemplate).convertAndSendToUser(
                "recipient@test.com", "/queue/messages", message);
    }

    @Test
    void sendChatMessage_ToDifferentRecipients() {
        ChatMessageResponse message = ChatMessageResponse.builder()
                .messageId(1L)
                .content("Hello!")
                .build();

        webSocketNotificationService.sendChatMessage("user1@test.com", message);
        webSocketNotificationService.sendChatMessage("user2@test.com", message);

        verify(messagingTemplate).convertAndSendToUser(
                "user1@test.com", "/queue/messages", message);
        verify(messagingTemplate).convertAndSendToUser(
                "user2@test.com", "/queue/messages", message);
    }

    @Test
    void sendTypingIndicator_TypingStarted() {
        TypingIndicator indicator = new TypingIndicator(10L, 1, "John Doe", true);

        webSocketNotificationService.sendTypingIndicator("recipient@test.com", indicator);

        verify(messagingTemplate).convertAndSendToUser(
                "recipient@test.com", "/queue/typing", indicator);
    }

    @Test
    void sendTypingIndicator_TypingStopped() {
        TypingIndicator indicator = new TypingIndicator(10L, 1, "John Doe", false);

        webSocketNotificationService.sendTypingIndicator("recipient@test.com", indicator);

        verify(messagingTemplate).convertAndSendToUser(
                "recipient@test.com", "/queue/typing", indicator);
    }

    @Test
    void pushNotification_Success() {
        Map<String, Object> notification = Map.of(
                "type", "LEAVE_APPROVED",
                "message", "Your leave request has been approved",
                "timestamp", LocalDateTime.now().toString()
        );

        webSocketNotificationService.pushNotification("employee@test.com", notification);

        verify(messagingTemplate).convertAndSendToUser(
                "employee@test.com", "/queue/notifications", notification);
    }

    @Test
    void pushNotification_DifferentTypes() {
        Map<String, Object> leaveNotification = Map.of("type", "LEAVE_APPROVED");
        Map<String, Object> attendanceNotification = Map.of("type", "ATTENDANCE_MARKED");

        webSocketNotificationService.pushNotification("user@test.com", leaveNotification);
        webSocketNotificationService.pushNotification("user@test.com", attendanceNotification);

        verify(messagingTemplate).convertAndSendToUser(
                "user@test.com", "/queue/notifications", leaveNotification);
        verify(messagingTemplate).convertAndSendToUser(
                "user@test.com", "/queue/notifications", attendanceNotification);
    }

    @Test
    void sendUnreadChatCount_Success() {
        webSocketNotificationService.sendUnreadChatCount("user@test.com", 5);

        verify(messagingTemplate).convertAndSendToUser(
                eq("user@test.com"),
                eq("/queue/chat-unread"),
                eq(Map.of("unreadCount", 5L))
        );
    }

    @Test
    void sendUnreadChatCount_ZeroCount() {
        webSocketNotificationService.sendUnreadChatCount("user@test.com", 0);

        verify(messagingTemplate).convertAndSendToUser(
                eq("user@test.com"),
                eq("/queue/chat-unread"),
                eq(Map.of("unreadCount", 0L))
        );
    }

    @Test
    void sendUnreadChatCount_LargeCount() {
        webSocketNotificationService.sendUnreadChatCount("user@test.com", 999);

        verify(messagingTemplate).convertAndSendToUser(
                eq("user@test.com"),
                eq("/queue/chat-unread"),
                eq(Map.of("unreadCount", 999L))
        );
    }
}
