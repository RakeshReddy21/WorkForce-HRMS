package org.example.workforce.repository;

import org.example.workforce.model.Employee;
import org.example.workforce.model.Goal;
import org.example.workforce.model.enums.GoalPriority;
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
class GoalRepositoryTest {

    @Autowired
    private GoalRepository goalRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private Employee employee;
    private Goal goal;

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

        goal = Goal.builder()
                .employee(employee)
                .title("Complete Project")
                .description("Finish the project on time")
                .priority(GoalPriority.HIGH)
                .year(2024)
                .deadline(java.time.LocalDate.now().plusMonths(3))
                .build();
    }

    @Test
    void testSaveAndFindById() {
        Goal saved = goalRepository.save(goal);
        assertNotNull(saved.getGoalId());
    }

    @Test
    void testFindByEmployee() {
        goalRepository.save(goal);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Goal> goals = goalRepository.findByEmployee_EmployeeId(employee.getEmployeeId(), pageable);
        assertFalse(goals.isEmpty());
    }

    @Test
    void testFindByEmployeeAndStatus() {
        goalRepository.save(goal);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Goal> goals = goalRepository.findByEmployee_EmployeeIdAndStatus(employee.getEmployeeId(), org.example.workforce.model.enums.GoalStatus.NOT_STARTED, pageable);
        assertFalse(goals.isEmpty());
    }
}

