package org.example.workforce.repository;

import org.example.workforce.model.ChatConversation;
import org.example.workforce.model.Employee;
import org.example.workforce.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ChatConversationRepositoryTest {

    @Autowired
    private ChatConversationRepository chatConversationRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private Employee employee1;
    private Employee employee2;
    private ChatConversation conversation;

    @BeforeEach
    void setUp() {
        employee1 = Employee.builder()
                .email("emp1@example.com")
                .employeeCode("EMP001")
                .firstName("John")
                .lastName("Doe")
                .passwordHash("$2a$10$hashedpassword")
                .joiningDate(java.time.LocalDate.now())
                .role(Role.EMPLOYEE)
                .isActive(true)
                .build();
        employee1 = employeeRepository.save(employee1);

        employee2 = Employee.builder()
                .email("emp2@example.com")
                .employeeCode("EMP002")
                .firstName("Jane")
                .lastName("Smith")
                .passwordHash("$2a$10$hashedpassword")
                .joiningDate(java.time.LocalDate.now())
                .role(Role.EMPLOYEE)
                .isActive(true)
                .build();
        employee2 = employeeRepository.save(employee2);

        conversation = ChatConversation.builder()
                .participant1(employee1)
                .participant2(employee2)
                .build();
    }

    @Test
    void testSaveAndFindById() {
        ChatConversation saved = chatConversationRepository.save(conversation);
        assertNotNull(saved.getConversationId());
    }

    @Test
    void testFindByParticipants() {
        chatConversationRepository.save(conversation);
        Optional<ChatConversation> found = chatConversationRepository.findByParticipants(employee1.getEmployeeId(), employee2.getEmployeeId());
        assertTrue(found.isPresent());
    }

    @Test
    void testFindAllByParticipant() {
        chatConversationRepository.save(conversation);
        assertFalse(chatConversationRepository.findAllByParticipant(employee1.getEmployeeId()).isEmpty());
    }
}

