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
import org.example.workforce.repository.ChatConversationRepository;
import org.example.workforce.repository.ChatMessageRepository;
import org.example.workforce.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChatService {

    @Autowired
    private ChatConversationRepository conversationRepository;
    @Autowired
    private ChatMessageRepository messageRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private PresenceService presenceService;
    @Autowired
    private WebSocketNotificationService wsNotificationService;

    @Transactional
    public ConversationResponse getOrCreateConversation(String currentEmail, Integer otherEmployeeId) {
        Employee currentUser = findEmployeeByEmail(currentEmail);
        Employee otherUser = employeeRepository.findById(otherEmployeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + otherEmployeeId));

        if (currentUser.getEmployeeId().equals(otherEmployeeId)) {
            throw new AccessDeniedException("Cannot create conversation with yourself");
        }

        ChatConversation conversation = conversationRepository
                .findByParticipants(currentUser.getEmployeeId(), otherEmployeeId)
                .orElseGet(() -> {

                    Employee p1, p2;
                    if (currentUser.getEmployeeId() < otherEmployeeId) {
                        p1 = currentUser;
                        p2 = otherUser;
                    } else {
                        p1 = otherUser;
                        p2 = currentUser;
                    }
                    ChatConversation newConv = ChatConversation.builder()
                            .participant1(p1)
                            .participant2(p2)
                            .build();
                    return conversationRepository.save(newConv);
                });

        return mapToConversationResponse(conversation, currentUser.getEmployeeId());
    }

    public List<ConversationResponse> getMyConversations(String email) {
        Employee employee = findEmployeeByEmail(email);
        List<ChatConversation> conversations = conversationRepository.findAllByParticipant(employee.getEmployeeId());

        return conversations.stream()
                .map(conv -> mapToConversationResponse(conv, employee.getEmployeeId()))
                .collect(Collectors.toList());
    }

    public Page<ChatMessageResponse> getMessages(String email, Long conversationId, int page, int size) {
        Employee employee = findEmployeeByEmail(email);
        ChatConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        if (!isParticipant(conversation, employee.getEmployeeId())) {
            throw new AccessDeniedException("You are not a participant of this conversation");
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<ChatMessage> messages = messageRepository
                .findByConversation_ConversationIdOrderByCreatedAtDesc(conversationId, pageable);

        return messages.map(msg -> mapToMessageResponse(msg, conversation, employee.getEmployeeId()));
    }

    @Transactional
    public ChatMessageResponse sendMessage(String senderEmail, ChatMessageRequest request) {
        Employee sender = findEmployeeByEmail(senderEmail);

        ChatConversation conversation;
        Employee recipient;

        if (request.getConversationId() != null) {
            conversation = conversationRepository.findById(request.getConversationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
            if (!isParticipant(conversation, sender.getEmployeeId())) {
                throw new AccessDeniedException("You are not a participant of this conversation");
            }
            recipient = getOtherParticipant(conversation, sender.getEmployeeId());
        } else if (request.getRecipientId() != null) {
            recipient = employeeRepository.findById(request.getRecipientId())
                    .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));

            Employee p1, p2;
            if (sender.getEmployeeId() < recipient.getEmployeeId()) {
                p1 = sender; p2 = recipient;
            } else {
                p1 = recipient; p2 = sender;
            }
            conversation = conversationRepository
                    .findByParticipants(sender.getEmployeeId(), recipient.getEmployeeId())
                    .orElseGet(() -> conversationRepository.save(
                            ChatConversation.builder().participant1(p1).participant2(p2).build()));
        } else {
            throw new AccessDeniedException("Either conversationId or recipientId must be provided");
        }

        MessageType messageType = MessageType.TEXT;
        if (request.getMessageType() != null) {
            try {
                messageType = MessageType.valueOf(request.getMessageType());
            } catch (IllegalArgumentException ignored) {}
        }

        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .sender(sender)
                .content(request.getContent())
                .messageType(messageType)
                .fileUrl(request.getFileUrl())
                .fileName(request.getFileName())
                .build();

        message = messageRepository.save(message);

        String preview = request.getContent();
        if (preview != null && preview.length() > 100) {
            preview = preview.substring(0, 100) + "...";
        }
        conversation.setLastMessageText(preview);
        conversation.setLastMessageAt(message.getCreatedAt() != null ? message.getCreatedAt() : LocalDateTime.now());
        conversation.setLastSenderId(sender.getEmployeeId());
        conversationRepository.save(conversation);

        ChatMessageResponse response = mapToMessageResponse(message, conversation, sender.getEmployeeId());
        response.setRecipientId(recipient.getEmployeeId());

        wsNotificationService.sendChatMessage(recipient.getEmail(), response);

        long unreadCount = messageRepository.countTotalUnreadForUser(recipient.getEmployeeId());
        wsNotificationService.sendUnreadChatCount(recipient.getEmail(), unreadCount);

        return response;
    }

    @Transactional
    public int markConversationAsRead(String email, Long conversationId) {
        Employee employee = findEmployeeByEmail(email);
        ChatConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));

        if (!isParticipant(conversation, employee.getEmployeeId())) {
            throw new AccessDeniedException("You are not a participant of this conversation");
        }

        int updated = messageRepository.markConversationAsRead(conversationId, employee.getEmployeeId());

        if (updated > 0) {
            long newUnreadCount = messageRepository.countTotalUnreadForUser(employee.getEmployeeId());
            wsNotificationService.sendUnreadChatCount(employee.getEmail(), newUnreadCount);
        }

        return updated;
    }

    public long getTotalUnreadCount(String email) {
        Employee employee = findEmployeeByEmail(email);
        return messageRepository.countTotalUnreadForUser(employee.getEmployeeId());
    }

    public Employee getOtherParticipantByConversation(Long conversationId, Integer currentEmpId) {
        ChatConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation not found"));
        return getOtherParticipant(conversation, currentEmpId);
    }

    private Employee findEmployeeByEmail(String email) {
        return employeeRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with email: " + email));
    }

    private boolean isParticipant(ChatConversation conv, Integer empId) {
        return conv.getParticipant1().getEmployeeId().equals(empId)
                || conv.getParticipant2().getEmployeeId().equals(empId);
    }

    private Employee getOtherParticipant(ChatConversation conv, Integer currentEmpId) {
        if (conv.getParticipant1().getEmployeeId().equals(currentEmpId)) {
            return conv.getParticipant2();
        }
        return conv.getParticipant1();
    }

    private ConversationResponse mapToConversationResponse(ChatConversation conv, Integer currentEmpId) {
        Employee other = getOtherParticipant(conv, currentEmpId);
        long unread = messageRepository.countByConversation_ConversationIdAndIsReadAndSender_EmployeeIdNot(
                conv.getConversationId(), false, currentEmpId);

        return ConversationResponse.builder()
                .conversationId(conv.getConversationId())
                .otherParticipantId(other.getEmployeeId())
                .otherParticipantName(other.getFirstName() + " " + other.getLastName())
                .otherParticipantCode(other.getEmployeeCode())
                .otherParticipantRole(other.getRole() != null ? other.getRole().name() : "")
                .otherParticipantDepartment(other.getDepartment() != null ? other.getDepartment().getDepartmentName() : "")
                .lastMessageText(conv.getLastMessageText())
                .lastSenderId(conv.getLastSenderId())
                .lastMessageAt(conv.getLastMessageAt())
                .unreadCount(unread)
                .online(presenceService.isOnline(other.getEmail()))
                .build();
    }

    private ChatMessageResponse mapToMessageResponse(ChatMessage msg, ChatConversation conv, Integer currentEmpId) {
        Employee other = getOtherParticipant(conv, msg.getSender().getEmployeeId());
        return ChatMessageResponse.builder()
                .messageId(msg.getMessageId())
                .conversationId(conv.getConversationId())
                .senderId(msg.getSender().getEmployeeId())
                .senderName(msg.getSender().getFirstName() + " " + msg.getSender().getLastName())
                .senderCode(msg.getSender().getEmployeeCode())
                .recipientId(other.getEmployeeId())
                .content(msg.getContent())
                .messageType(msg.getMessageType().name())
                .fileUrl(msg.getFileUrl())
                .fileName(msg.getFileName())
                .isRead(msg.getIsRead())
                .createdAt(msg.getCreatedAt())
                .build();
    }
}
