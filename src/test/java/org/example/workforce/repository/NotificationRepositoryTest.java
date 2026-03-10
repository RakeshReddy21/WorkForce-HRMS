package org.example.workforce.repository;

import org.example.workforce.model.Employee;
import org.example.workforce.model.Notification;
import org.example.workforce.model.enums.NotificationType;
import org.example.workforce.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Transactional
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private Employee employee;
    private Notification notification;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
                .email("test@example.com")
                .employeeCode("EMP001")
                .firstName("John")
                .lastName("Doe")
                .passwordHash("$2a$10$hashedpassword")
                .joiningDate(java.time.LocalDate.now())
                .role(Role.EMPLOYEE)
                .isActive(true)
                .build();
        employee = employeeRepository.save(employee);

        notification = Notification.builder()
                .recipient(employee)
                .title("Test Notification")
                .message("Test Message")
                .type(NotificationType.LEAVE_APPROVED)
                .isRead(false)
                .build();
    }

    @Test
    void testSaveAndFindById() {
        Notification saved = notificationRepository.save(notification);
        assertNotNull(saved.getNotificationId());
    }

    @Test
    void testFindByRecipientOrderByCreatedAtDesc() {
        notificationRepository.save(notification);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> notifications = notificationRepository.findByRecipient_EmployeeIdOrderByCreatedAtDesc(employee.getEmployeeId(), pageable);
        assertTrue(notifications.getTotalElements() > 0);
    }

    @Test
    void testFindByRecipientAndIsRead() {
        notificationRepository.save(notification);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> notifications = notificationRepository.findByRecipient_EmployeeIdAndIsReadOrderByCreatedAtDesc(employee.getEmployeeId(), false, pageable);
        assertTrue(notifications.getTotalElements() > 0);
    }

    @Test
    void testCountByRecipientAndIsRead() {
        notificationRepository.save(notification);
        long count = notificationRepository.countByRecipient_EmployeeIdAndIsRead(employee.getEmployeeId(), false);
        assertTrue(count > 0);
    }

    @Test
    void testMarkAllAsRead() {
        notificationRepository.save(notification);
        int updated = notificationRepository.markAllAsRead(employee.getEmployeeId());
        assertTrue(updated > 0);
    }

    @Test
    void testFindByRecipientAndType() {
        notificationRepository.save(notification);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Notification> notifications = notificationRepository.findByRecipient_EmployeeIdAndTypeOrderByCreatedAtDesc(employee.getEmployeeId(), NotificationType.LEAVE_APPROVED, pageable);
        assertTrue(notifications.getTotalElements() > 0);
    }
}

