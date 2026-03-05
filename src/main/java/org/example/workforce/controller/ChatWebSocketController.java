package org.example.workforce.controller;

import org.example.workforce.dto.ChatMessageRequest;
import org.example.workforce.dto.ChatMessageResponse;
import org.example.workforce.dto.TypingIndicator;
import org.example.workforce.model.Employee;
import org.example.workforce.repository.EmployeeRepository;
import org.example.workforce.service.ChatService;
import org.example.workforce.service.WebSocketNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class ChatWebSocketController {

    @Autowired
    private ChatService chatService;
    @Autowired
    private WebSocketNotificationService wsNotificationService;
    @Autowired
    private EmployeeRepository employeeRepository;

    @MessageMapping("/chat.send")
    public void sendMessage(@Payload ChatMessageRequest request, Principal principal) {
        String senderEmail = principal.getName();
        chatService.sendMessage(senderEmail, request);

    }

    @MessageMapping("/chat.typing")
    public void typingIndicator(@Payload TypingIndicator indicator, Principal principal) {
        String senderEmail = principal.getName();
        Employee sender = employeeRepository.findByEmail(senderEmail).orElse(null);
        if (sender == null) return;

        indicator.setSenderId(sender.getEmployeeId());
        indicator.setSenderName(sender.getFirstName() + " " + sender.getLastName());

        Employee recipient = getRecipientFromConversation(indicator.getConversationId(), sender.getEmployeeId());
        if (recipient != null) {
            wsNotificationService.sendTypingIndicator(recipient.getEmail(), indicator);
        }
    }

    private Employee getRecipientFromConversation(Long conversationId, Integer senderId) {
        if (conversationId == null) return null;
        return chatService.getOtherParticipantByConversation(conversationId, senderId);
    }
}
