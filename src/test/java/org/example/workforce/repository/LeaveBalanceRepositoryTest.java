package org.example.workforce.repository;

import org.example.workforce.model.Employee;
import org.example.workforce.model.LeaveBalance;
import org.example.workforce.model.LeaveType;
import org.example.workforce.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true",
    "spring.jpa.properties.hibernate.format_sql=true",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.globally_quoted_identifiers=true",
    "spring.jpa.properties.hibernate.jdbc.time_zone=UTC",
    "spring.jpa.properties.hibernate.hbm2ddl.auto=create-drop"
})
class LeaveBalanceRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private LeaveTypeRepository leaveTypeRepository;

    private Employee employee;
    private LeaveType leaveType;
    private LeaveBalance leaveBalance;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
                .email("employee@test.com")
                .firstName("John")
                .lastName("Doe")
                .employeeCode("EMP001")
                .role(Role.EMPLOYEE)
                .isActive(true)
                .build();
        employee = entityManager.persistAndFlush(employee);

        leaveType = LeaveType.builder()
                .leaveTypeName("Casual Leave")
                .defaultDays(10)
                .isPaidLeave(true)
                .isActive(true)
                .build();
        leaveType = entityManager.persistAndFlush(leaveType);

        leaveBalance = LeaveBalance.builder()
                .employee(employee)
                .leaveType(leaveType)
                .year(2024)
                .totalLeaves(10)
                .usedLeaves(0)
                .build();
        leaveBalance = entityManager.persistAndFlush(leaveBalance);
    }

    @Test
    void testFindByEmployee_EmployeeIdAndYear() {

        List<LeaveBalance> result = leaveBalanceRepository.findByEmployee_EmployeeIdAndYear(
                employee.getEmployeeId(), 2024);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(leaveBalance.getBalanceId(), result.get(0).getBalanceId());
    }

    @Test
    void testFindByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear() {

        Optional<LeaveBalance> result = leaveBalanceRepository
                .findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                        employee.getEmployeeId(), leaveType.getLeaveTypeId(), 2024);

        assertTrue(result.isPresent());
        assertEquals(leaveBalance.getBalanceId(), result.get().getBalanceId());
    }

    @Test
    void testExistsByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear() {

        boolean exists = leaveBalanceRepository.existsByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                employee.getEmployeeId(), leaveType.getLeaveTypeId(), 2024);

        assertTrue(exists);
    }

    @Test
    void testSaveLeaveBalance() {

        LeaveType newLeaveType = LeaveType.builder()
                .leaveTypeName("Sick Leave")
                .defaultDays(12)
                .isPaidLeave(true)
                .isActive(true)
                .build();
        newLeaveType = entityManager.persistAndFlush(newLeaveType);

        LeaveBalance newBalance = LeaveBalance.builder()
                .employee(employee)
                .leaveType(newLeaveType)
                .year(2024)
                .totalLeaves(12)
                .usedLeaves(0)
                .build();

        LeaveBalance saved = leaveBalanceRepository.save(newBalance);

        assertNotNull(saved.getBalanceId());
        assertEquals(12, saved.getTotalLeaves());
    }
}
