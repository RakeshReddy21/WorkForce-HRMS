package org.example.workforce.repository;

import org.example.workforce.model.ChatConversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    @Query("SELECT c FROM ChatConversation c WHERE " +
            "(c.participant1.employeeId = :empId OR c.participant2.employeeId = :empId) " +
            "ORDER BY c.lastMessageAt DESC NULLS LAST")
    List<ChatConversation> findAllByParticipant(@Param("empId") Integer empId);

    @Query("SELECT c FROM ChatConversation c WHERE " +
            "(c.participant1.employeeId = :emp1 AND c.participant2.employeeId = :emp2) OR " +
            "(c.participant1.employeeId = :emp2 AND c.participant2.employeeId = :emp1)")
    Optional<ChatConversation> findByParticipants(@Param("emp1") Integer emp1, @Param("emp2") Integer emp2);
}
