package org.example.workforce.repository;

import org.example.workforce.model.Announcement;
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
class AnnouncementRepositoryTest {

    @Autowired
    private AnnouncementRepository announcementRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private Employee employee;
    private Announcement announcement;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
                .email("admin@example.com")
                .employeeCode("ADM001")
                .firstName("Admin")
                .lastName("User")
                .passwordHash("$2a$10$hashedpassword")
                .joiningDate(java.time.LocalDate.now())
                .role(Role.ADMIN)
                .isActive(true)
                .build();
        employee = employeeRepository.save(employee);

        announcement = Announcement.builder()
                .title("Test Announcement")
                .content("Test Content")
                .isActive(true)
                .createdBy(employee)
                .build();
    }

    @Test
    void testSaveAndFindById() {
        Announcement saved = announcementRepository.save(announcement);
        assertNotNull(saved.getAnnouncementId());
    }

    @Test
    void testFindByIsActiveOrderByCreatedAtDesc() {
        announcementRepository.save(announcement);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Announcement> announcements = announcementRepository.findByIsActiveOrderByCreatedAtDesc(true, pageable);
        assertTrue(announcements.getTotalElements() > 0);
    }

    @Test
    void testFindAllByOrderByCreatedAtDesc() {
        announcementRepository.save(announcement);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Announcement> announcements = announcementRepository.findAllByOrderByCreatedAtDesc(pageable);
        assertTrue(announcements.getTotalElements() > 0);
    }

    @Test
    void testFindByCreatedBy() {
        announcementRepository.save(announcement);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Announcement> announcements = announcementRepository.findByCreatedBy_EmployeeIdOrderByCreatedAtDesc(employee.getEmployeeId(), pageable);
        assertTrue(announcements.getTotalElements() > 0);
    }
}

