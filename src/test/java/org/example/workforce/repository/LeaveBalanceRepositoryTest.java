package org.example.workforce.repository;

import org.example.workforce.model.Employee;
import org.example.workforce.model.LeaveBalance;
import org.example.workforce.model.LeaveType;
import org.example.workforce.model.enums.Gender;
import org.example.workforce.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=YEAR",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true",
    "spring.jpa.properties.hibernate.format_sql=true",
    "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
    "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
class LeaveBalanceRepositoryTest {

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
                .email("admin@workforce.com")
                .firstName("System")
                .lastName("Admin")
                .employeeCode("ADM001")
                .passwordHash("$2a$10$a0QnKrkXFVR3rKbnqpPHwu1BDucXt96Rf/a1MVeyqd5eKILLHZ7Eu")
                .phone("0000000000")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .gender(Gender.MALE)
                .address("WorkForce HQ")
                .joiningDate(LocalDate.of(2026, 2, 26))
                .salary(BigDecimal.ZERO)
                .role(Role.ADMIN)
                .isActive(true)
                .twoFactorEnabled(false)
                .build();
        employee = employeeRepository.saveAndFlush(employee);

        leaveType = LeaveType.builder()
                .leaveTypeName("Casual Leave")
                .defaultDays(10)
                .isPaidLeave(true)
                .isActive(true)
                .build();
        leaveType = leaveTypeRepository.saveAndFlush(leaveType);

        leaveBalance = LeaveBalance.builder()
                .employee(employee)
                .leaveType(leaveType)
                .year(2024)
                .totalLeaves(10)
                .usedLeaves(0)
                .build();
        leaveBalance = leaveBalanceRepository.saveAndFlush(leaveBalance);
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
        newLeaveType = leaveTypeRepository.saveAndFlush(newLeaveType);

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
