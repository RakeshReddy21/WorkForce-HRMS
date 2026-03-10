package org.example.workforce.repository;

import org.example.workforce.model.ActivityLog;
import org.example.workforce.model.Employee;
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

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class ActivityLogRepositoryTest {

    @Autowired
    private ActivityLogRepository activityLogRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private Employee employee;
    private ActivityLog activityLog;

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

        activityLog = ActivityLog.builder()
                .performedBy(employee)
                .action("LOGIN")
                .entityType("EMPLOYEE")
                .entityId(employee.getEmployeeId())
                .details("User logged in")
                .ipAddress("127.0.0.1")
                .build();
    }

    @Test
    void testSaveAndFindById() {
        ActivityLog saved = activityLogRepository.save(activityLog);
        assertNotNull(saved.getLogId());
    }

    @Test
    void testFindByPerformedByOrderByCreatedAtDesc() {
        activityLogRepository.save(activityLog);
        Pageable pageable = PageRequest.of(0, 10);
        Page<ActivityLog> logs = activityLogRepository.findByPerformedBy_EmployeeIdOrderByCreatedAtDesc(employee.getEmployeeId(), pageable);
        assertTrue(logs.getTotalElements() > 0);
    }
}

