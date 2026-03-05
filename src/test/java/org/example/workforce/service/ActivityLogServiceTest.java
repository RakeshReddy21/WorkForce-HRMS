package org.example.workforce.service;

import org.example.workforce.model.ActivityLog;
import org.example.workforce.model.Employee;
import org.example.workforce.model.enums.Role;
import org.example.workforce.repository.ActivityLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ActivityLogServiceTest {

    @Mock
    private ActivityLogRepository activityLogRepository;

    @InjectMocks
    private ActivityLogService activityLogService;

    private ActivityLog activityLog;
    private Employee employee;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        pageable = PageRequest.of(0, 10);

        employee = Employee.builder()
                .employeeId(1)
                .email("emp@test.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.ADMIN)
                .build();

        activityLog = ActivityLog.builder()
                .logId(1)
                .entityType("EMPLOYEE")
                .entityId(1)
                .action("CREATED")
                .details("Employee created")
                .performedBy(employee)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void getAllLogs_Success() {
        Page<ActivityLog> page = new PageImpl<>(List.of(activityLog));
        when(activityLogRepository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(page);

        Page<ActivityLog> result = activityLogService.getAllLogs(pageable);

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("EMPLOYEE", result.getContent().get(0).getEntityType());
    }

    @Test
    void getAllLogs_Empty() {
        Page<ActivityLog> emptyPage = new PageImpl<>(List.of());
        when(activityLogRepository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(emptyPage);

        Page<ActivityLog> result = activityLogService.getAllLogs(pageable);

        assertEquals(0, result.getTotalElements());
    }

    @Test
    void getLogsByEntityType_Success() {
        Page<ActivityLog> page = new PageImpl<>(List.of(activityLog));
        when(activityLogRepository.findByEntityTypeOrderByCreatedAtDesc("EMPLOYEE", pageable))
                .thenReturn(page);

        Page<ActivityLog> result = activityLogService.getLogsByEntityType("EMPLOYEE", pageable);

        assertEquals(1, result.getTotalElements());
        assertEquals("EMPLOYEE", result.getContent().get(0).getEntityType());
    }

    @Test
    void getLogsByEntityType_NoResults() {
        when(activityLogRepository.findByEntityTypeOrderByCreatedAtDesc("UNKNOWN", pageable))
                .thenReturn(new PageImpl<>(List.of()));

        Page<ActivityLog> result = activityLogService.getLogsByEntityType("UNKNOWN", pageable);

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void getLogsByEmployee_Success() {
        Page<ActivityLog> page = new PageImpl<>(List.of(activityLog));
        when(activityLogRepository.findByPerformedBy_EmployeeIdOrderByCreatedAtDesc(1, pageable))
                .thenReturn(page);

        Page<ActivityLog> result = activityLogService.getLogsByEmployee(1, pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getLogsByEmployee_NoActivity() {
        when(activityLogRepository.findByPerformedBy_EmployeeIdOrderByCreatedAtDesc(99, pageable))
                .thenReturn(new PageImpl<>(List.of()));

        Page<ActivityLog> result = activityLogService.getLogsByEmployee(99, pageable);

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void getLogsByEntity_Success() {
        when(activityLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("EMPLOYEE", 1))
                .thenReturn(List.of(activityLog));

        List<ActivityLog> result = activityLogService.getLogsByEntity("EMPLOYEE", 1);

        assertEquals(1, result.size());
        assertEquals("CREATED", result.get(0).getAction());
    }

    @Test
    void getLogsByEntity_NoLogs() {
        when(activityLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc("LEAVE", 99))
                .thenReturn(List.of());

        List<ActivityLog> result = activityLogService.getLogsByEntity("LEAVE", 99);

        assertTrue(result.isEmpty());
    }

    @Test
    void getLogsByDateRange_Success() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 5);
        Page<ActivityLog> page = new PageImpl<>(List.of(activityLog));

        when(activityLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                start.atStartOfDay(), end.atTime(23, 59, 59), pageable))
                .thenReturn(page);

        Page<ActivityLog> result = activityLogService.getLogsByDateRange(start, end, pageable);

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void getLogsByDateRange_NoLogsInRange() {
        LocalDate start = LocalDate.of(2020, 1, 1);
        LocalDate end = LocalDate.of(2020, 1, 31);

        when(activityLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                start.atStartOfDay(), end.atTime(23, 59, 59), pageable))
                .thenReturn(new PageImpl<>(List.of()));

        Page<ActivityLog> result = activityLogService.getLogsByDateRange(start, end, pageable);

        assertTrue(result.getContent().isEmpty());
    }

    @Test
    void getLogsByDateRange_SameDay() {
        LocalDate sameDay = LocalDate.of(2026, 3, 5);
        Page<ActivityLog> page = new PageImpl<>(List.of(activityLog));

        when(activityLogRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                sameDay.atStartOfDay(), sameDay.atTime(23, 59, 59), pageable))
                .thenReturn(page);

        Page<ActivityLog> result = activityLogService.getLogsByDateRange(sameDay, sameDay, pageable);

        assertEquals(1, result.getTotalElements());
    }
}
