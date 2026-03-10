package org.example.workforce.service;

import org.example.workforce.dto.CheckInRequest;
import org.example.workforce.dto.CheckOutRequest;
import org.example.workforce.exception.*;
import org.example.workforce.model.Attendance;
import org.example.workforce.model.Employee;
import org.example.workforce.model.enums.Role;
import org.example.workforce.repository.AttendanceRepository;
import org.example.workforce.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.example.workforce.dto.AttendanceResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private GeoAttendanceService geoAttendanceService;

    @InjectMocks
    private AttendanceService attendanceService;

    private Employee employee;
    private Attendance attendance;

    @BeforeEach
    void setUp() {
        // Inject @Value fields that Mockito doesn't handle
        ReflectionTestUtils.setField(attendanceService, "officeStartTime", "09:00");
        ReflectionTestUtils.setField(attendanceService, "officeEndTime", "18:00");
        ReflectionTestUtils.setField(attendanceService, "lateThresholdMinutes", 15);
        ReflectionTestUtils.setField(attendanceService, "earlyDepartureThresholdMinutes", 30);

        employee = Employee.builder()
                .employeeId(1)
                .email("employee@test.com")
                .firstName("John")
                .lastName("Doe")
                .employeeCode("EMP001")
                .role(Role.EMPLOYEE)
                .isActive(true)
                .build();

        attendance = Attendance.builder()
                .attendanceId(1)
                .employee(employee)
                .attendanceDate(LocalDate.now())
                .checkInTime(LocalDateTime.of(LocalDate.now(), LocalTime.of(9, 0)))
                .build();
    }

    @Test
    void testCheckIn_Success() {

        CheckInRequest request = new CheckInRequest();
        request.setNotes("On time");
        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(attendanceRepository.existsByEmployee_EmployeeIdAndAttendanceDate(
                employee.getEmployeeId(), LocalDate.now())).thenReturn(false);
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(i -> i.getArgument(0));

        AttendanceResponse result = attendanceService.checkIn("employee@test.com", request, "192.168.1.1");

        assertNotNull(result);
        assertNotNull(result.getCheckInTime());
        verify(attendanceRepository, times(1)).save(any(Attendance.class));
        verify(geoAttendanceService, never()).verifyLocation(anyDouble(), anyDouble());
    }

    @Test
    void testCheckIn_AlreadyCheckedIn() {

        CheckInRequest request = new CheckInRequest();
        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(attendanceRepository.existsByEmployee_EmployeeIdAndAttendanceDate(
                employee.getEmployeeId(), LocalDate.now())).thenReturn(true);
        when(attendanceRepository.findByEmployee_EmployeeIdAndAttendanceDate(
                employee.getEmployeeId(), LocalDate.now())).thenReturn(Optional.of(attendance));

        assertThrows(DuplicateResourceException.class, () -> {
            attendanceService.checkIn("employee@test.com", request, "192.168.1.1");
        });
    }

    @Test
    void testCheckOut_Success() {

        CheckOutRequest request = new CheckOutRequest();
        request.setNotes("Leaving early");
        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(attendanceRepository.findByEmployee_EmployeeIdAndAttendanceDate(
                employee.getEmployeeId(), LocalDate.now())).thenReturn(Optional.of(attendance));
        when(attendanceRepository.save(any(Attendance.class))).thenAnswer(i -> i.getArgument(0));

        AttendanceResponse result = attendanceService.checkOut("employee@test.com", request, "192.168.1.1");

        assertNotNull(result);
        assertNotNull(result.getCheckOutTime());
        verify(attendanceRepository, times(1)).save(attendance);
        verify(geoAttendanceService, never()).verifyLocation(anyDouble(), anyDouble());
    }

    @Test
    void testCheckOut_NotCheckedIn() {

        CheckOutRequest request = new CheckOutRequest();
        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(attendanceRepository.findByEmployee_EmployeeIdAndAttendanceDate(
                employee.getEmployeeId(), LocalDate.now())).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            attendanceService.checkOut("employee@test.com", request, "192.168.1.1");
        });
    }

    @Test
    void testGetTodayAttendance_Success() {

        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(attendanceRepository.findByEmployee_EmployeeIdAndAttendanceDate(
                employee.getEmployeeId(), LocalDate.now())).thenReturn(Optional.of(attendance));

        AttendanceResponse result = attendanceService.getTodayStatus("employee@test.com");

        assertNotNull(result);
        assertEquals(attendance.getAttendanceId(), result.getAttendanceId());
    }
}
