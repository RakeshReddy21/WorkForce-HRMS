package org.example.workforce.service;

import org.example.workforce.dto.DashboardResponse;
import org.example.workforce.dto.EmployeeDashboardResponse;
import org.example.workforce.dto.EmployeeReportResponse;
import org.example.workforce.exception.ResourceNotFoundException;
import org.example.workforce.model.*;
import org.example.workforce.model.enums.LeaveStatus;
import org.example.workforce.model.enums.Role;
import org.example.workforce.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private LeaveApplicationRepository leaveApplicationRepository;

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private DesignationRepository designationRepository;

    @Mock
    private HolidayRepository holidayRepository;

    @Mock
    private PerformanceReviewRepository performanceReviewRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private DashboardService dashboardService;

    private Employee employee;
    private Department department;
    private Designation designation;

    @BeforeEach
    void setUp() {
        department = Department.builder()
                .departmentId(1)
                .departmentName("Engineering")
                .build();

        designation = Designation.builder()
                .designationId(1)
                .designationName("Software Engineer")
                .build();

        employee = Employee.builder()
                .employeeId(1)
                .email("emp@test.com")
                .firstName("John")
                .lastName("Doe")
                .employeeCode("EMP001")
                .department(department)
                .designation(designation)
                .role(Role.EMPLOYEE)
                .isActive(true)
                .joiningDate(LocalDate.of(2023, 1, 15))
                .build();
    }

    @Test
    void getDashboard_Success() {
        when(employeeRepository.count()).thenReturn(50L);
        when(employeeRepository.countByIsActive(true)).thenReturn(45L);
        when(employeeRepository.countByIsActive(false)).thenReturn(5L);
        when(employeeRepository.countByRoleAndIsActive(Role.MANAGER, true)).thenReturn(10L);
        when(employeeRepository.countByRoleAndIsActive(Role.ADMIN, true)).thenReturn(2L);
        when(employeeRepository.countByRoleAndIsActive(Role.EMPLOYEE, true)).thenReturn(38L);
        when(leaveApplicationRepository.countByStatus(LeaveStatus.PENDING)).thenReturn(3L);
        when(leaveApplicationRepository.findActiveLeavesToday(eq(LeaveStatus.APPROVED), any(LocalDate.class)))
                .thenReturn(List.of());
        when(departmentRepository.count()).thenReturn(5L);
        when(designationRepository.count()).thenReturn(8L);
        when(employeeRepository.countActiveByDepartment()).thenReturn(
                List.of(new Object[]{"Engineering", 20L}, new Object[]{"HR", 10L})
        );

        DashboardResponse result = dashboardService.getDashboard();

        assertNotNull(result);
        assertEquals(50L, result.getTotalEmployees());
        assertEquals(45L, result.getActiveEmployees());
        assertEquals(5L, result.getInactiveEmployees());
        assertEquals(10L, result.getTotalManagers());
        assertEquals(2L, result.getTotalAdmins());
        assertEquals(38L, result.getTotalRegularEmployees());
        assertEquals(3L, result.getPendingLeaves());
        assertEquals(5L, result.getTotalDepartments());
        assertEquals(8L, result.getTotalDesignations());
        assertNotNull(result.getEmployeesByDepartment());
        assertEquals(2, result.getEmployeesByDepartment().size());
    }

    @Test
    void getLeaveReport_WithYear_Success() {
        LeaveType leaveType = LeaveType.builder().leaveTypeName("Annual Leave").build();
        LeaveBalance balance = LeaveBalance.builder()
                .employee(employee)
                .leaveType(leaveType)
                .totalLeaves(20)
                .usedLeaves(5)
                .year(2026)
                .build();
        when(leaveBalanceRepository.findAll()).thenReturn(List.of(balance));

        var result = dashboardService.getLeaveReport(2026);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("EMP001", result.get(0).getEmployeeCode());
        assertEquals("Annual Leave", result.get(0).getLeaveTypeName());
    }

    @Test
    void getLeaveReport_NullYear_UsesCurrentYear() {
        when(leaveBalanceRepository.findAll()).thenReturn(List.of());

        var result = dashboardService.getLeaveReport(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getLeaveReportByDepartment_Success() {
        LeaveType leaveType = LeaveType.builder().leaveTypeName("Sick Leave").build();
        LeaveBalance balance = LeaveBalance.builder()
                .employee(employee)
                .leaveType(leaveType)
                .totalLeaves(10)
                .usedLeaves(2)
                .year(2026)
                .build();
        when(leaveBalanceRepository.findAll()).thenReturn(List.of(balance));

        var result = dashboardService.getLeaveReportByDepartment(1, 2026);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Engineering", result.get(0).getDepartmentName());
    }

    @Test
    void getLeaveReportByDepartment_NoMatchingDept_ReturnsEmpty() {
        LeaveType leaveType = LeaveType.builder().leaveTypeName("Sick Leave").build();
        LeaveBalance balance = LeaveBalance.builder()
                .employee(employee)
                .leaveType(leaveType)
                .totalLeaves(10)
                .usedLeaves(2)
                .year(2026)
                .build();
        when(leaveBalanceRepository.findAll()).thenReturn(List.of(balance));

        var result = dashboardService.getLeaveReportByDepartment(99, 2026);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getLeaveReportByEmployee_Success() {
        LeaveType leaveType = LeaveType.builder().leaveTypeName("Annual Leave").build();
        LeaveBalance balance = LeaveBalance.builder()
                .employee(employee)
                .leaveType(leaveType)
                .totalLeaves(20)
                .usedLeaves(5)
                .year(2026)
                .build();
        when(leaveBalanceRepository.findAll()).thenReturn(List.of(balance));

        var result = dashboardService.getLeaveReportByEmployee("EMP001", 2026);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("John Doe", result.get(0).getEmployeeName());
    }

    @Test
    void getLeaveReportByEmployee_NotFound_ReturnsEmpty() {
        when(leaveBalanceRepository.findAll()).thenReturn(List.of());

        var result = dashboardService.getLeaveReportByEmployee("NONEXIST", 2026);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getEmployeeDashboard_Success() {
        int currentYear = LocalDate.now().getYear();
        LeaveType leaveType = LeaveType.builder().leaveTypeName("Annual Leave").build();
        LeaveBalance balance = LeaveBalance.builder()
                .employee(employee)
                .leaveType(leaveType)
                .totalLeaves(20).usedLeaves(5)
                .year(currentYear).build();

        when(employeeRepository.findByEmail("emp@test.com")).thenReturn(Optional.of(employee));
        when(leaveBalanceRepository.findByEmployee_EmployeeIdAndYear(1, currentYear)).thenReturn(List.of(balance));
        when(leaveApplicationRepository.countByEmployee_EmployeeIdAndStatus(1, LeaveStatus.PENDING)).thenReturn(2L);
        when(leaveApplicationRepository.countByEmployee_EmployeeIdAndStatus(1, LeaveStatus.APPROVED)).thenReturn(3L);
        when(holidayRepository.findByHolidayDateBetween(any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(List.of());
        when(notificationRepository.countByRecipient_EmployeeIdAndIsRead(1, false)).thenReturn(5L);

        EmployeeDashboardResponse result = dashboardService.getEmployeeDashboard("emp@test.com");

        assertNotNull(result);
        assertEquals("John Doe", result.getEmployeeName());
        assertEquals("EMP001", result.getEmployeeCode());
        assertEquals("Engineering", result.getDepartmentName());
        assertEquals("Software Engineer", result.getDesignationTitle());
        assertEquals(2L, result.getPendingLeaveRequests());
        assertEquals(3L, result.getApprovedLeaves());
        assertEquals(5L, result.getUnreadNotifications());
        assertEquals(1, result.getLeaveBalances().size());
    }

    @Test
    void getEmployeeDashboard_EmployeeNotFound_ThrowsException() {
        when(employeeRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> dashboardService.getEmployeeDashboard("unknown@test.com"));
    }

    @Test
    void getEmployeeDashboard_NoDepartment_ShowsNA() {
        employee.setDepartment(null);
        employee.setDesignation(null);
        int currentYear = LocalDate.now().getYear();

        when(employeeRepository.findByEmail("emp@test.com")).thenReturn(Optional.of(employee));
        when(leaveBalanceRepository.findByEmployee_EmployeeIdAndYear(1, currentYear)).thenReturn(List.of());
        when(leaveApplicationRepository.countByEmployee_EmployeeIdAndStatus(eq(1), any())).thenReturn(0L);
        when(holidayRepository.findByHolidayDateBetween(any(), any())).thenReturn(List.of());
        when(notificationRepository.countByRecipient_EmployeeIdAndIsRead(1, false)).thenReturn(0L);

        EmployeeDashboardResponse result = dashboardService.getEmployeeDashboard("emp@test.com");

        assertEquals("N/A", result.getDepartmentName());
        assertEquals("N/A", result.getDesignationTitle());
    }

    @Test
    void getEmployeeReport_Success() {
        when(employeeRepository.count()).thenReturn(50L);
        when(employeeRepository.countByIsActive(true)).thenReturn(45L);
        when(employeeRepository.countByIsActive(false)).thenReturn(5L);
        when(employeeRepository.countActiveByDepartment()).thenReturn(
                Collections.singletonList(new Object[]{"Engineering", 20L})
        );
        List<Object[]> roleCounts = Arrays.asList(
                new Object[]{Role.EMPLOYEE, 35L},
                new Object[]{Role.MANAGER, 10L},
                new Object[]{Role.ADMIN, 2L}
        );
        when(employeeRepository.countActiveByRole()).thenReturn(roleCounts);
        List<Object[]> joiningTrends = Collections.singletonList(new Object[]{2026, 1, 5L});
        when(employeeRepository.getJoiningTrends()).thenReturn(joiningTrends);
        when(employeeRepository.findAll()).thenReturn(List.of(employee));

        EmployeeReportResponse result = dashboardService.getEmployeeReport();

        assertNotNull(result);
        assertEquals(50L, result.getTotalEmployees());
        assertEquals(45L, result.getActiveEmployees());
        assertEquals(5L, result.getInactiveEmployees());
        assertNotNull(result.getHeadcountByDepartment());
        assertNotNull(result.getHeadcountByRole());
        assertNotNull(result.getJoiningTrends());
    }

    @Test
    void getEmployeeReport_NoActiveEmployees_ZeroTenure() {
        Employee inactiveEmp = Employee.builder()
                .employeeId(2).isActive(false).joiningDate(LocalDate.of(2023, 1, 1)).build();
        when(employeeRepository.count()).thenReturn(1L);
        when(employeeRepository.countByIsActive(true)).thenReturn(0L);
        when(employeeRepository.countByIsActive(false)).thenReturn(1L);
        when(employeeRepository.countActiveByDepartment()).thenReturn(List.of());
        when(employeeRepository.countActiveByRole()).thenReturn(List.of());
        when(employeeRepository.getJoiningTrends()).thenReturn(List.of());
        when(employeeRepository.findAll()).thenReturn(List.of(inactiveEmp));

        EmployeeReportResponse result = dashboardService.getEmployeeReport();

        assertEquals(0, result.getAverageTenureMonths());
    }
}
