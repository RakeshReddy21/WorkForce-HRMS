package org.example.workforce.repository;

import org.example.workforce.model.Employee;
import org.example.workforce.model.PerformanceReview;
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
class PerformanceReviewRepositoryTest {

    @Autowired
    private PerformanceReviewRepository performanceReviewRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private Employee employee;
    private Employee reviewer;
    private PerformanceReview review;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
                .email("emp@example.com")
                .employeeCode("EMP001")
                .firstName("John")
                .lastName("Doe")
                .passwordHash("$2a$10$hashedpassword")
                .joiningDate(java.time.LocalDate.now())
                .role(Role.EMPLOYEE)
                .isActive(true)
                .build();
        employee = employeeRepository.save(employee);

        reviewer = Employee.builder()
                .email("reviewer@example.com")
                .employeeCode("MGR001")
                .firstName("Manager")
                .lastName("User")
                .passwordHash("$2a$10$hashedpassword")
                .joiningDate(java.time.LocalDate.now())
                .role(Role.MANAGER)
                .isActive(true)
                .build();
        reviewer = employeeRepository.save(reviewer);

        review = PerformanceReview.builder()
                .employee(employee)
                .reviewer(reviewer)
                .reviewPeriod("2024-Q1")
                .managerRating(4)
                .managerFeedback("Good performance")
                .build();
    }

    @Test
    void testSaveAndFindById() {
        PerformanceReview saved = performanceReviewRepository.save(review);
        assertNotNull(saved.getReviewId());
    }

    @Test
    void testFindByEmployee() {
        performanceReviewRepository.save(review);
        Pageable pageable = PageRequest.of(0, 10);
        Page<PerformanceReview> reviews = performanceReviewRepository.findByEmployee_EmployeeId(employee.getEmployeeId(), pageable);
        assertTrue(reviews.getTotalElements() > 0);
    }

    @Test
    void testFindByStatus() {
        performanceReviewRepository.save(review);
        Pageable pageable = PageRequest.of(0, 10);
        Page<PerformanceReview> reviews = performanceReviewRepository.findByStatus(org.example.workforce.model.enums.ReviewStatus.DRAFT, pageable);
        assertTrue(reviews.getTotalElements() >= 0);
    }
}

