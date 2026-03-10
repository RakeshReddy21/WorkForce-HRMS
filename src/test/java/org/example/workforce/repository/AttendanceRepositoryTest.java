package org.example.workforce.repository;

import org.example.workforce.model.Attendance;
import org.example.workforce.model.Employee;
import org.example.workforce.model.enums.AttendanceStatus;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AttendanceRepositoryTest {

    @Autowired
    private AttendanceRepository attendanceRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private Employee employee;
    private Attendance attendance;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
                .email("test@example.com")
                .employeeCode("EMP001")
                .firstName("John")
                .lastName("Doe")
                .passwordHash("$2a$10$hashedpassword")
                .joiningDate(LocalDate.now())
                .role(Role.EMPLOYEE)
                .isActive(true)
                .build();
        employee = employeeRepository.save(employee);

        attendance = Attendance.builder()
                .employee(employee)
                .attendanceDate(LocalDate.now())
                .checkInTime(LocalDateTime.now())
                .status(AttendanceStatus.PRESENT)
                .build();
    }

    @Test
    void testSaveAndFindById() {
        Attendance saved = attendanceRepository.save(attendance);
        Optional<Attendance> found = attendanceRepository.findById(saved.getAttendanceId());
        assertTrue(found.isPresent());
        assertEquals(AttendanceStatus.PRESENT, found.get().getStatus());
    }

    @Test
    void testFindByEmployeeAndAttendanceDate() {
        attendanceRepository.save(attendance);
        Optional<Attendance> found = attendanceRepository.findByEmployee_EmployeeIdAndAttendanceDate(employee.getEmployeeId(), LocalDate.now());
        assertTrue(found.isPresent());
    }

    @Test
    void testFindByEmployeeAndAttendanceDateBetween() {
        attendanceRepository.save(attendance);
        LocalDate start = LocalDate.now().minusDays(1);
        LocalDate end = LocalDate.now().plusDays(1);
        List<Attendance> attendances = attendanceRepository.findByEmployee_EmployeeIdAndAttendanceDateBetween(employee.getEmployeeId(), start, end);
        assertFalse(attendances.isEmpty());
    }

    @Test
    void testFindByEmployeeOrderByAttendanceDateDesc() {
        attendanceRepository.save(attendance);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Attendance> attendances = attendanceRepository.findByEmployee_EmployeeIdOrderByAttendanceDateDesc(employee.getEmployeeId(), pageable);
        assertFalse(attendances.isEmpty());
    }

    @Test
    void testFindAllByDate() {
        attendanceRepository.save(attendance);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Attendance> attendances = attendanceRepository.findAllByDate(LocalDate.now(), pageable);
        assertFalse(attendances.isEmpty());
    }
}

