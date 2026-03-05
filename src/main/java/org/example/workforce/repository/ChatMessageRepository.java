package org.example.workforce.repository;

import org.example.workforce.model.ChatMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findByConversation_ConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    long countByConversation_ConversationIdAndIsReadAndSender_EmployeeIdNot(
            Long conversationId, Boolean isRead, Integer senderId);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.conversation.conversationId IN " +
            "(SELECT c.conversationId FROM ChatConversation c WHERE " +
            "c.participant1.employeeId = :empId OR c.participant2.employeeId = :empId) " +
            "AND m.isRead = false AND m.sender.employeeId != :empId")
    long countTotalUnreadForUser(@Param("empId") Integer empId);

    @Modifying
    @Query("UPDATE ChatMessage m SET m.isRead = true WHERE m.conversation.conversationId = :convId " +
            "AND m.sender.employeeId != :readerId AND m.isRead = false")
    int markConversationAsRead(@Param("convId") Long conversationId, @Param("readerId") Integer readerId);
}
