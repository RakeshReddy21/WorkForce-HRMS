package org.example.workforce.service;

import org.example.workforce.dto.LeaveApplyRequest;
import org.example.workforce.exception.*;
import org.example.workforce.model.*;
import org.example.workforce.model.enums.LeaveStatus;
import org.example.workforce.repository.*;
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
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LeaveServiceTest {

    @Mock
    private LeaveApplicationRepository leaveApplicationRepository;

    @Mock
    private LeaveBalanceRepository leaveBalanceRepository;

    @Mock
    private LeaveTypeRepository leaveTypeRepository;

    @Mock
    private HolidayRepository holidayRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private LeaveService leaveService;

    private Employee employee;
    private LeaveType leaveType;
    private LeaveBalance leaveBalance;
    private LeaveApplyRequest leaveApplyRequest;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
                .employeeId(1)
                .email("employee@test.com")
                .firstName("John")
                .lastName("Doe")
                .employeeCode("EMP001")
                .isActive(true)
                .build();

        leaveType = LeaveType.builder()
                .leaveTypeId(1)
                .leaveTypeName("Casual Leave")
                .defaultDays(10)
                .isPaidLeave(true)
                .isActive(true)
                .isLossOfPay(false)
                .build();

        leaveBalance = LeaveBalance.builder()
                .balanceId(1)
                .employee(employee)
                .leaveType(leaveType)
                .year(2024)
                .totalLeaves(10)
                .usedLeaves(0)
                .build();

        leaveApplyRequest = new LeaveApplyRequest();
        leaveApplyRequest.setLeaveTypeId(1);
        leaveApplyRequest.setStartDate(LocalDate.now().plusDays(1));
        leaveApplyRequest.setEndDate(LocalDate.now().plusDays(3));
        leaveApplyRequest.setReason("Personal work");
    }

    @Test
    void testApplyLeave_Success() {

        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(leaveApplicationRepository.findOverlappingLeaves(anyInt(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(leaveBalanceRepository.findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                anyInt(), anyInt(), anyInt())).thenReturn(Optional.of(leaveBalance));
        when(leaveApplicationRepository.save(any(LeaveApplication.class))).thenAnswer(i -> i.getArgument(0));

        LeaveApplication result = leaveService.applyLeave("employee@test.com", leaveApplyRequest);

        assertNotNull(result);
        assertEquals(LeaveStatus.PENDING, result.getStatus());
        assertEquals(employee, result.getEmployee());
        assertEquals(leaveType, result.getLeaveType());
        verify(leaveApplicationRepository, times(1)).save(any(LeaveApplication.class));
        verify(notificationService, times(1)).notifyLeaveApplied(employee);
    }

    @Test
    void testApplyLeave_EmployeeNotFound() {

        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            leaveService.applyLeave("employee@test.com", leaveApplyRequest);
        });
    }

    @Test
    void testApplyLeave_LeaveTypeNotFound() {

        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            leaveService.applyLeave("employee@test.com", leaveApplyRequest);
        });
    }

    @Test
    void testApplyLeave_InactiveLeaveType() {

        leaveType.setIsActive(false);
        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));

        assertThrows(InvalidActionException.class, () -> {
            leaveService.applyLeave("employee@test.com", leaveApplyRequest);
        });
    }

    @Test
    void testApplyLeave_EndDateBeforeStartDate() {

        leaveApplyRequest.setEndDate(LocalDate.now().plusDays(1));
        leaveApplyRequest.setStartDate(LocalDate.now().plusDays(3));
        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));

        assertThrows(BadRequestException.class, () -> {
            leaveService.applyLeave("employee@test.com", leaveApplyRequest);
        });
    }

    @Test
    void testApplyLeave_PastDate() {

        leaveApplyRequest.setStartDate(LocalDate.now().minusDays(1));
        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));

        assertThrows(BadRequestException.class, () -> {
            leaveService.applyLeave("employee@test.com", leaveApplyRequest);
        });
    }

    @Test
    void testApplyLeave_OverlappingLeaves() {

        LeaveApplication existingLeave = LeaveApplication.builder()
                .leaveId(1)
                .employee(employee)
                .startDate(LocalDate.now().plusDays(1))
                .endDate(LocalDate.now().plusDays(2))
                .status(LeaveStatus.PENDING)
                .build();
        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(leaveApplicationRepository.findOverlappingLeaves(anyInt(), any(), any(), any(), any()))
                .thenReturn(Collections.singletonList(existingLeave));

        assertThrows(DuplicateResourceException.class, () -> {
            leaveService.applyLeave("employee@test.com", leaveApplyRequest);
        });
    }

    @Test
    void testApplyLeave_InsufficientBalance() {

        leaveBalance.setTotalLeaves(5);
        leaveBalance.setUsedLeaves(3);
        // Available balance = 5 - 3 = 2 days
        // Set dates to ensure we get at least 3 working days (more than available)
        LocalDate startDate = LocalDate.now().plusDays(1);
        // Skip weekends to ensure we get working days
        while (startDate.getDayOfWeek().getValue() > 5) {
            startDate = startDate.plusDays(1);
        }
        leaveApplyRequest.setStartDate(startDate);
        // Set end date to get at least 3 working days (Monday to Wednesday = 3 days)
        LocalDate endDate = startDate;
        int workingDays = 0;
        while (workingDays < 3) {
            if (endDate.getDayOfWeek().getValue() <= 5) {
                workingDays++;
            }
            if (workingDays < 3) {
                endDate = endDate.plusDays(1);
            }
        }
        leaveApplyRequest.setEndDate(endDate);
        
        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(leaveApplicationRepository.findOverlappingLeaves(anyInt(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(holidayRepository.findByHolidayDateBetween(any(), any())).thenReturn(Collections.emptyList());
        when(leaveBalanceRepository.findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                anyInt(), anyInt(), anyInt())).thenReturn(Optional.of(leaveBalance));

        assertThrows(InsufficientBalanceException.class, () -> {
            leaveService.applyLeave("employee@test.com", leaveApplyRequest);
        });
    }

    @Test
    void testApplyLeave_AutoCreateBalance() {

        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(leaveApplicationRepository.findOverlappingLeaves(anyInt(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(leaveBalanceRepository.findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                anyInt(), anyInt(), anyInt())).thenReturn(Optional.empty());
        when(leaveBalanceRepository.save(any(LeaveBalance.class))).thenAnswer(i -> {
            LeaveBalance balance = i.getArgument(0);
            balance.setBalanceId(1);
            return balance;
        });
        when(leaveApplicationRepository.save(any(LeaveApplication.class))).thenAnswer(i -> i.getArgument(0));

        LeaveApplication result = leaveService.applyLeave("employee@test.com", leaveApplyRequest);

        assertNotNull(result);
        verify(leaveBalanceRepository, times(1)).save(any(LeaveBalance.class));
        verify(leaveApplicationRepository, times(1)).save(any(LeaveApplication.class));
    }

    @Test
    void testApplyLeave_LossOfPay_NoBalanceCheck() {

        leaveType.setIsLossOfPay(true);
        leaveBalance.setTotalLeaves(0);
        leaveBalance.setUsedLeaves(0);
        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(leaveTypeRepository.findById(1)).thenReturn(Optional.of(leaveType));
        when(leaveApplicationRepository.findOverlappingLeaves(anyInt(), any(), any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(leaveBalanceRepository.findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                anyInt(), anyInt(), anyInt())).thenReturn(Optional.of(leaveBalance));
        when(leaveApplicationRepository.save(any(LeaveApplication.class))).thenAnswer(i -> i.getArgument(0));

        LeaveApplication result = leaveService.applyLeave("employee@test.com", leaveApplyRequest);

        assertNotNull(result);
        assertEquals(LeaveStatus.PENDING, result.getStatus());
        verify(leaveApplicationRepository, times(1)).save(any(LeaveApplication.class));
    }

    @Test
    void testGetMyLeaveBalance_ReturnsAllActiveLeaveTypes() {

        int currentYear = LocalDate.now().getYear();
        List<LeaveBalance> existingBalances = new ArrayList<>();
        LeaveType sickLeave = LeaveType.builder()
                .leaveTypeId(2)
                .leaveTypeName("Sick Leave")
                .defaultDays(12)
                .isActive(true)
                .build();
        existingBalances.add(leaveBalance);

        List<LeaveType> allActiveLeaveTypes = Arrays.asList(leaveType, sickLeave);

        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(leaveBalanceRepository.findByEmployee_EmployeeIdAndYear(employee.getEmployeeId(), currentYear))
                .thenReturn(existingBalances);
        when(leaveTypeRepository.findByIsActive(true)).thenReturn(allActiveLeaveTypes);

        List<LeaveBalance> result = leaveService.getMyLeaveBalance("employee@test.com");

        assertNotNull(result);
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(b -> b.getLeaveType().getLeaveTypeId() == 1));
        assertTrue(result.stream().anyMatch(b -> b.getLeaveType().getLeaveTypeId() == 2));
    }

    @Test
    void testCancelLeave_Success() {

        LeaveApplication leave = LeaveApplication.builder()
                .leaveId(1)
                .employee(employee)
                .status(LeaveStatus.PENDING)
                .build();
        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(leaveApplicationRepository.findById(1)).thenReturn(Optional.of(leave));
        when(leaveApplicationRepository.save(any(LeaveApplication.class))).thenAnswer(i -> i.getArgument(0));

        LeaveApplication result = leaveService.cancelLeave("employee@test.com", 1);

        assertNotNull(result);
        assertEquals(LeaveStatus.CANCELLED, result.getStatus());
        verify(leaveApplicationRepository, times(1)).save(leave);
        verify(notificationService, times(1)).notifyLeaveCancelled(employee, 1);
    }

    @Test
    void testCancelLeave_NotOwnLeave() {

        Employee otherEmployee = Employee.builder().employeeId(2).build();
        LeaveApplication leave = LeaveApplication.builder()
                .leaveId(1)
                .employee(otherEmployee)
                .status(LeaveStatus.PENDING)
                .build();
        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(leaveApplicationRepository.findById(1)).thenReturn(Optional.of(leave));

        assertThrows(AccessDeniedException.class, () -> {
            leaveService.cancelLeave("employee@test.com", 1);
        });
    }

    @Test
    void testCancelLeave_AlreadyApproved() {
        LocalDate startDate = LocalDate.now().plusDays(1);
        LocalDate endDate = LocalDate.now().plusDays(3);
        int year = startDate.getYear();

        LeaveApplication leave = LeaveApplication.builder()
                .leaveId(1)
                .employee(employee)
                .leaveType(leaveType)
                .startDate(startDate)
                .endDate(endDate)
                .totalDays(3)
                .status(LeaveStatus.APPROVED)
                .build();
        
        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(leaveApplicationRepository.findById(1)).thenReturn(Optional.of(leave));
        when(leaveBalanceRepository.findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                employee.getEmployeeId(), leaveType.getLeaveTypeId(), year))
                .thenReturn(Optional.of(leaveBalance));
        when(leaveApplicationRepository.save(any(LeaveApplication.class))).thenAnswer(i -> i.getArgument(0));

        LeaveApplication result = leaveService.cancelLeave("employee@test.com", 1);

        assertNotNull(result);
        assertEquals(LeaveStatus.CANCELLED, result.getStatus());
        verify(leaveBalanceRepository, times(1)).save(any(LeaveBalance.class));
    }
}
