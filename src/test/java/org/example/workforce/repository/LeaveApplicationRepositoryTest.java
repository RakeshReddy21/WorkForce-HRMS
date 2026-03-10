package org.example.workforce.repository;

import org.example.workforce.model.Employee;
import org.example.workforce.model.LeaveApplication;
import org.example.workforce.model.LeaveType;
import org.example.workforce.model.enums.LeaveStatus;
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

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class LeaveApplicationRepositoryTest {

    @Autowired
    private LeaveApplicationRepository leaveApplicationRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    private Employee employee;
    private LeaveType leaveType;
    private LeaveApplication leaveApplication;

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

        leaveType = LeaveType.builder()
                .leaveTypeName("Annual Leave")
                .defaultDays(20)
                .isActive(true)
                .build();
        leaveType = leaveTypeRepository.save(leaveType);

        leaveApplication = LeaveApplication.builder()
                .employee(employee)
                .leaveType(leaveType)
                .startDate(LocalDate.now())
                .endDate(LocalDate.now().plusDays(5))
                .totalDays(5)
                .reason("Vacation")
                .status(LeaveStatus.PENDING)
                .build();
    }

    @Test
    void testSaveAndFindById() {
        LeaveApplication saved = leaveApplicationRepository.save(leaveApplication);
        Optional<LeaveApplication> found = leaveApplicationRepository.findById(saved.getLeaveId());
        assertTrue(found.isPresent());
        assertEquals(LeaveStatus.PENDING, found.get().getStatus());
    }

    @Test
    void testFindByEmployee() {
        leaveApplicationRepository.save(leaveApplication);
        Pageable pageable = PageRequest.of(0, 10);
        Page<LeaveApplication> applications = leaveApplicationRepository.findByEmployee_EmployeeId(employee.getEmployeeId(), pageable);
        assertFalse(applications.isEmpty());
    }

    @Test
    void testFindByStatus() {
        leaveApplicationRepository.save(leaveApplication);
        Pageable pageable = PageRequest.of(0, 10);
        Page<LeaveApplication> applications = leaveApplicationRepository.findByStatus(LeaveStatus.PENDING, pageable);
        assertFalse(applications.isEmpty());
    }

    @Test
    void testFindByEmployeeAndStatus() {
        leaveApplicationRepository.save(leaveApplication);
        Pageable pageable = PageRequest.of(0, 10);
        Page<LeaveApplication> applications = leaveApplicationRepository.findByEmployee_EmployeeIdAndStatus(employee.getEmployeeId(), LeaveStatus.PENDING, pageable);
        assertFalse(applications.isEmpty());
    }
}

