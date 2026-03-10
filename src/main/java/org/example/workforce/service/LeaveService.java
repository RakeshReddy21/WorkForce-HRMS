package org.example.workforce.service;

import org.example.workforce.dto.*;
import org.example.workforce.exception.*;
import org.example.workforce.model.*;
import org.example.workforce.model.enums.LeaveStatus;
import org.example.workforce.model.enums.Role;
import org.example.workforce.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class LeaveService {
    @Autowired
    private LeaveApplicationRepository leaveApplicationRepository;
    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;
    @Autowired
    private LeaveTypeRepository leaveTypeRepository;
    @Autowired
    private HolidayRepository holidayRepository;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private NotificationService notificationService;

    @Transactional
    public LeaveApplication applyLeave(String email, LeaveApplyRequest request) {
        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with email: " + email));
        LeaveType leaveType = leaveTypeRepository.findById(request.getLeaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found with id: " + request.getLeaveTypeId()));
        if (!leaveType.getIsActive()) {
            throw new InvalidActionException("This leave type is not active");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }
        if (request.getStartDate().isBefore(LocalDate.now())) {
            throw new BadRequestException("Cannot apply leave for past dates");
        }
        List<LeaveApplication> overlapping = leaveApplicationRepository.findOverlappingLeaves(
                employee.getEmployeeId(), request.getStartDate(), request.getEndDate(),
                LeaveStatus.CANCELLED, LeaveStatus.REJECTED);
        if (!overlapping.isEmpty()) {
            throw new DuplicateResourceException("You already have a leave application overlapping with these dates");
        }
        int totalDays = calculateWorkingDays(request.getStartDate(), request.getEndDate());
        if (totalDays <= 0) {
            throw new BadRequestException("No working days in the selected date range");
        }
        int currentYear = request.getStartDate().getYear();

        LeaveBalance balance = leaveBalanceRepository
                .findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                        employee.getEmployeeId(), leaveType.getLeaveTypeId(), currentYear)
                .orElseGet(() -> {

                    Integer defaultTotal = leaveType.getIsLossOfPay() ? 0 :
                            (leaveType.getDefaultDays() != null ? leaveType.getDefaultDays() : 0);

                    LeaveBalance newBalance = LeaveBalance.builder()
                            .employee(employee)
                            .leaveType(leaveType)
                            .year(currentYear)
                            .totalLeaves(defaultTotal)
                            .usedLeaves(0)
                            .adjustmentReason("Auto-created on leave application")
                            .build();
                    return leaveBalanceRepository.save(newBalance);
                });

        if (!leaveType.getIsLossOfPay() && balance.getAvailableBalance() < totalDays) {
            throw new InsufficientBalanceException(
                    "Insufficient leave balance. Available: " + balance.getAvailableBalance() + ", Requested: " + totalDays);
        }
        LeaveApplication leave = LeaveApplication.builder()
                .employee(employee).leaveType(leaveType)
                .startDate(request.getStartDate()).endDate(request.getEndDate())
                .totalDays(totalDays).reason(request.getReason())
                .status(LeaveStatus.PENDING).appliedDate(LocalDateTime.now())
                .build();
        LeaveApplication savedLeave = leaveApplicationRepository.save(leave);
        notificationService.notifyLeaveApplied(employee);
        return savedLeave;
    }

    public Page<LeaveApplication> getMyLeaves(String email, LeaveStatus status, Pageable pageable) {
        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with email: " + email));
        if (status != null) {
            return leaveApplicationRepository.findByEmployee_EmployeeIdAndStatus(employee.getEmployeeId(), status, pageable);
        }
        return leaveApplicationRepository.findByEmployee_EmployeeId(employee.getEmployeeId(), pageable);
    }

    @Transactional
    public LeaveApplication cancelLeave(String email, Integer leaveId) {
        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with email: " + email));
        LeaveApplication leave = leaveApplicationRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave application not found with id: " + leaveId));
        if (!leave.getEmployee().getEmployeeId().equals(employee.getEmployeeId())) {
            throw new AccessDeniedException("You can only cancel your own leave applications");
        }
        if (leave.getStatus() != LeaveStatus.PENDING && leave.getStatus() != LeaveStatus.APPROVED) {
            throw new InvalidActionException("Only pending or approved leaves can be cancelled. Current status: " + leave.getStatus());
        }

        // If the leave was APPROVED, restore the leave balance
        if (leave.getStatus() == LeaveStatus.APPROVED) {
            int year = leave.getStartDate().getYear();
            leaveBalanceRepository.findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                    employee.getEmployeeId(), leave.getLeaveType().getLeaveTypeId(), year)
                    .ifPresent(balance -> {
                        int restored = Math.max(balance.getUsedLeaves() - leave.getTotalDays(), 0);
                        balance.setUsedLeaves(restored);
                        leaveBalanceRepository.save(balance);
                    });
        }

        leave.setStatus(LeaveStatus.CANCELLED);
        LeaveApplication cancelledLeave = leaveApplicationRepository.save(leave);
        notificationService.notifyLeaveCancelled(employee, leaveId);
        return cancelledLeave;
    }

    public List<LeaveBalance> getMyLeaveBalance(String email) {
        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with email: " + email));
        int currentYear = LocalDate.now().getYear();

        List<LeaveBalance> balances = leaveBalanceRepository.findByEmployee_EmployeeIdAndYear(employee.getEmployeeId(), currentYear);

        List<LeaveType> allActiveLeaveTypes = leaveTypeRepository.findByIsActive(true);

        Set<Integer> existingLeaveTypeIds = balances.stream()
                .map(b -> b.getLeaveType().getLeaveTypeId())
                .collect(Collectors.toSet());

        for (LeaveType leaveType : allActiveLeaveTypes) {
            if (!existingLeaveTypeIds.contains(leaveType.getLeaveTypeId())) {

                LeaveBalance zeroBalance = LeaveBalance.builder()
                        .employee(employee)
                        .leaveType(leaveType)
                        .year(currentYear)
                        .totalLeaves(leaveType.getDefaultDays() != null ? leaveType.getDefaultDays() : 0)
                        .usedLeaves(0)
                        .build();
                balances.add(zeroBalance);
            }
        }

        return balances;
    }

    public List<Holiday> getHolidays(Integer year) {
        return holidayRepository.findByYearOrderByHolidayDateAsc(year != null ? year : LocalDate.now().getYear());
    }

    public Page<LeaveApplication> getTeamLeaves(String managerEmail, LeaveStatus status, Pageable pageable) {
        Employee manager = employeeRepository.findByEmail(managerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found with email: " + managerEmail));

        if (status != null) {
            return leaveApplicationRepository.findByManagerCodeAndStatusExcludingManagers(manager.getEmployeeCode(), status, pageable);
        }
        return leaveApplicationRepository.findByManagerCodeExcludingManagers(manager.getEmployeeCode(), pageable);
    }

    @Transactional
    public LeaveApplication actionLeave(String managerEmail, Integer leaveId, LeaveActionRequest request) {
        Employee manager = employeeRepository.findByEmail(managerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found with email: " + managerEmail));
        LeaveApplication leave = leaveApplicationRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave application not found with id: " + leaveId));
        if (leave.getEmployee().getManager() == null ||
                !leave.getEmployee().getManager().getEmployeeCode().equals(manager.getEmployeeCode())) {
            throw new AccessDeniedException("This leave application does not belong to your team");
        }

        if (leave.getEmployee().getRole() == Role.MANAGER) {
            throw new AccessDeniedException("Manager leave applications can only be actioned by an admin");
        }
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new InvalidActionException("Only pending leaves can be approved/rejected. Current status: " + leave.getStatus());
        }
        LeaveStatus newStatus;
        if ("APPROVED".equalsIgnoreCase(request.getAction())) {
            newStatus = LeaveStatus.APPROVED;
        } else if ("REJECTED".equalsIgnoreCase(request.getAction())) {
            if (request.getComments() == null || request.getComments().isBlank()) {
                throw new BadRequestException("Comments are mandatory when rejecting a leave");
            }
            newStatus = LeaveStatus.REJECTED;
        } else {
            throw new BadRequestException("Invalid action. Use APPROVED or REJECTED");
        }
        leave.setStatus(newStatus);
        leave.setManagerComments(request.getComments());
        leave.setActionedBy(manager);
        leave.setActionDate(LocalDateTime.now());
        if (newStatus == LeaveStatus.APPROVED) {
            int year = leave.getStartDate().getYear();
            LeaveBalance balance = leaveBalanceRepository
                    .findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                            leave.getEmployee().getEmployeeId(), leave.getLeaveType().getLeaveTypeId(), year)
                    .orElseThrow(() -> new ResourceNotFoundException("Leave balance not found for employee"));
            balance.setUsedLeaves(balance.getUsedLeaves() + leave.getTotalDays());
            leaveBalanceRepository.save(balance);
        }
        LeaveApplication actionedLeave = leaveApplicationRepository.save(leave);
        if (newStatus == LeaveStatus.APPROVED) {
            notificationService.notifyLeaveApproved(leave.getEmployee(), actionedLeave.getLeaveId());
        } else if (newStatus == LeaveStatus.REJECTED) {
            notificationService.notifyLeaveRejected(leave.getEmployee(), actionedLeave.getLeaveId());
        }
        return actionedLeave;
    }

    @Transactional
    public LeaveApplication adminActionLeave(String adminEmail, Integer leaveId, LeaveActionRequest request) {
        Employee admin = employeeRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found with email: " + adminEmail));
        LeaveApplication leave = leaveApplicationRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave application not found with id: " + leaveId));
        if (leave.getStatus() != LeaveStatus.PENDING) {
            throw new InvalidActionException("Only pending leaves can be approved/rejected. Current status: " + leave.getStatus());
        }
        LeaveStatus newStatus;
        if ("APPROVED".equalsIgnoreCase(request.getAction())) {
            newStatus = LeaveStatus.APPROVED;
        } else if ("REJECTED".equalsIgnoreCase(request.getAction())) {
            if (request.getComments() == null || request.getComments().isBlank()) {
                throw new BadRequestException("Comments are mandatory when rejecting a leave");
            }
            newStatus = LeaveStatus.REJECTED;
        } else {
            throw new BadRequestException("Invalid action. Use APPROVED or REJECTED");
        }
        leave.setStatus(newStatus);
        leave.setManagerComments(request.getComments());
        leave.setActionedBy(admin);
        leave.setActionDate(LocalDateTime.now());
        if (newStatus == LeaveStatus.APPROVED) {
            int year = leave.getStartDate().getYear();
            LeaveBalance balance = leaveBalanceRepository
                    .findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                            leave.getEmployee().getEmployeeId(), leave.getLeaveType().getLeaveTypeId(), year)
                    .orElseThrow(() -> new ResourceNotFoundException("Leave balance not found for employee"));
            balance.setUsedLeaves(balance.getUsedLeaves() + leave.getTotalDays());
            leaveBalanceRepository.save(balance);
        }
        LeaveApplication actionedLeave = leaveApplicationRepository.save(leave);
        if (newStatus == LeaveStatus.APPROVED) {
            notificationService.notifyLeaveApproved(leave.getEmployee(), actionedLeave.getLeaveId());
        } else if (newStatus == LeaveStatus.REJECTED) {
            notificationService.notifyLeaveRejected(leave.getEmployee(), actionedLeave.getLeaveId());
        }
        return actionedLeave;
    }

    public List<LeaveBalance> getTeamMemberBalance(String managerEmail, String employeeCode) {
        Employee manager = employeeRepository.findByEmail(managerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found with email: " + managerEmail));
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with code: " + employeeCode));
        if (employee.getManager() == null ||
                !employee.getManager().getEmployeeCode().equals(manager.getEmployeeCode())) {
            throw new AccessDeniedException("This employee is not in your team");
        }
        int currentYear = LocalDate.now().getYear();
        return leaveBalanceRepository.findByEmployee_EmployeeIdAndYear(employee.getEmployeeId(), currentYear);
    }

    public LeaveType createLeaveType(LeaveTypeRequest request) {
        if (leaveTypeRepository.existsByLeaveTypeName(request.getLeaveTypeName())) {
            throw new DuplicateResourceException("Leave type '" + request.getLeaveTypeName() + "' already exists");
        }
        LeaveType leaveType = LeaveType.builder()
                .leaveTypeName(request.getLeaveTypeName())
                .description(request.getDescription())
                .defaultDays(request.getDefaultDays())
                .isPaidLeave(request.getIsPaidLeave() != null ? request.getIsPaidLeave() : true)
                .isCarryForwardEnabled(request.getIsCarryForwardEnabled() != null ? request.getIsCarryForwardEnabled() : false)
                .maxCarryForwardDays(request.getMaxCarryForwardDays() != null ? request.getMaxCarryForwardDays() : 0)
                .isLossOfPay(request.getIsLossOfPay() != null ? request.getIsLossOfPay() : false)
                .build();
        return leaveTypeRepository.save(leaveType);
    }

    public List<LeaveType> getAllLeaveType() {
        return leaveTypeRepository.findAll();
    }

    public LeaveType updateLeaveType(Integer leaveTypeId, LeaveTypeRequest request) {
        LeaveType leaveType = leaveTypeRepository.findById(leaveTypeId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found with id: " + leaveTypeId));
        leaveTypeRepository.findByLeaveTypeName(request.getLeaveTypeName()).ifPresent(existing -> {
            if (!existing.getLeaveTypeId().equals(leaveTypeId)) {
                throw new DuplicateResourceException("Leave type '" + request.getLeaveTypeName() + "' already exists");
            }
        });
        leaveType.setLeaveTypeName(request.getLeaveTypeName());
        leaveType.setDescription(request.getDescription());
        leaveType.setDefaultDays(request.getDefaultDays());
        if (request.getIsPaidLeave() != null) {
            leaveType.setIsPaidLeave(request.getIsPaidLeave());
        }
        if (request.getIsCarryForwardEnabled() != null) {
            leaveType.setIsCarryForwardEnabled(request.getIsCarryForwardEnabled());
        }
        if (request.getMaxCarryForwardDays() != null) {
            leaveType.setMaxCarryForwardDays(request.getMaxCarryForwardDays());
        }
        if (request.getIsLossOfPay() != null) {
            leaveType.setIsLossOfPay(request.getIsLossOfPay());
        }
        return leaveTypeRepository.save(leaveType);
    }

    @Transactional
    public LeaveBalance assignLeaveQuota(String employeeCode, AdjustLeaveBalanceRequest request, String adminEmail) {
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with code: " + employeeCode));
        Employee admin = employeeRepository.findByEmail(adminEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Admin not found with email: " + adminEmail));
        LeaveType leaveType = leaveTypeRepository.findById(request.getLeaveTypeId())
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found with id: " + request.getLeaveTypeId()));
        int currentYear = LocalDate.now().getYear();
        LeaveBalance balance = leaveBalanceRepository
                .findByEmployee_EmployeeIdAndLeaveType_LeaveTypeIdAndYear(
                        employee.getEmployeeId(), leaveType.getLeaveTypeId(), currentYear)
                .orElse(LeaveBalance.builder()
                        .employee(employee).leaveType(leaveType).year(currentYear).usedLeaves(0)
                        .build());
        balance.setTotalLeaves(request.getTotalLeaves());
        balance.setAdjustmentReason(request.getReason());
        balance.setAdjustedBy(admin);
        return leaveBalanceRepository.save(balance);
    }

    public List<LeaveBalance> getEmployeeBalance(String employeeCode) {
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with code: " + employeeCode));
        int currentYear = LocalDate.now().getYear();
        return leaveBalanceRepository.findByEmployee_EmployeeIdAndYear(employee.getEmployeeId(), currentYear);
    }

    public Holiday createHoliday(HolidayRequest request) {
        if (holidayRepository.existsByHolidayDate(request.getHolidayDate())) {
            throw new DuplicateResourceException("A holiday already exists on date: " + request.getHolidayDate());
        }
        Holiday holiday = Holiday.builder()
                .holidayName(request.getHolidayName())
                .holidayDate(request.getHolidayDate())
                .description(request.getDescription())
                .year(request.getHolidayDate().getYear())
                .build();
        return holidayRepository.save(holiday);
    }

    public Holiday updateHoliday(Integer holidayId, HolidayRequest request) {
        Holiday holiday = holidayRepository.findById(holidayId)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found with id: " + holidayId));
        if (!holiday.getHolidayDate().equals(request.getHolidayDate()) &&
                holidayRepository.existsByHolidayDate(request.getHolidayDate())) {
            throw new DuplicateResourceException("A holiday already exists on date: " + request.getHolidayDate());
        }
        holiday.setHolidayName(request.getHolidayName());
        holiday.setHolidayDate(request.getHolidayDate());
        holiday.setDescription(request.getDescription());
        holiday.setYear(request.getHolidayDate().getYear());
        return holidayRepository.save(holiday);
    }

    public void deleteHoliday(Integer holidayId) {
        if (!holidayRepository.existsById(holidayId)) {
            throw new ResourceNotFoundException("Holiday not found with id: " + holidayId);
        }
        holidayRepository.deleteById(holidayId);
    }

    public List<TeamLeaveCalendarEntry> getTeamLeaveCalendar(String managerEmail, LocalDate startDate, LocalDate endDate) {
        Employee manager = employeeRepository.findByEmail(managerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found with email: " + managerEmail));

        if (startDate == null) startDate = LocalDate.now().withDayOfMonth(1);
        if (endDate == null) endDate = startDate.plusMonths(1).minusDays(1);

        List<LeaveApplication> approvedLeaves = leaveApplicationRepository.findTeamLeavesBetween(
                manager.getEmployeeCode(), LeaveStatus.APPROVED, startDate, endDate);

        return approvedLeaves.stream()
                .map(la -> TeamLeaveCalendarEntry.builder()
                        .employeeCode(la.getEmployee().getEmployeeCode())
                        .employeeName(la.getEmployee().getFirstName() + " " + la.getEmployee().getLastName())
                        .leaveTypeName(la.getLeaveType().getLeaveTypeName())
                        .startDate(la.getStartDate())
                        .endDate(la.getEndDate())
                        .totalDays(la.getTotalDays())
                        .status(la.getStatus().name())
                        .build())
                .collect(Collectors.toList());
    }

    public Page<LeaveApplication> getAllLeaveApplications(LeaveStatus status, Pageable pageable) {
        if (status != null) {
            return leaveApplicationRepository.findByStatus(status, pageable);
        }
        return leaveApplicationRepository.findAll(pageable);
    }

    private int calculateWorkingDays(LocalDate startDate, LocalDate endDate) {
        List<Holiday> holidays = holidayRepository.findByHolidayDateBetween(startDate, endDate);
        int workingDays = 0;
        LocalDate date = startDate;
        while (!date.isAfter(endDate)) {
            DayOfWeek day = date.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                LocalDate findDate = date;
                boolean isHoliday = holidays.stream().anyMatch(h -> h.getHolidayDate().equals(findDate));
                if (!isHoliday) {
                    workingDays++;
                }
            }
            date = date.plusDays(1);
        }
        return workingDays;
    }
}
