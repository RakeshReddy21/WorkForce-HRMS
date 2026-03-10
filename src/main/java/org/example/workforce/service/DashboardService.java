package org.example.workforce.service;

import org.example.workforce.dto.DashboardResponse;
import org.example.workforce.dto.EmployeeDashboardResponse;
import org.example.workforce.dto.EmployeeReportResponse;
import org.example.workforce.dto.LeaveReportResponse;
import org.example.workforce.exception.ResourceNotFoundException;
import org.example.workforce.model.Employee;
import org.example.workforce.model.Holiday;
import org.example.workforce.model.LeaveBalance;
import org.example.workforce.model.enums.LeaveStatus;
import org.example.workforce.model.enums.Role;
import org.example.workforce.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DashboardService {
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private LeaveApplicationRepository leaveApplicationRepository;
    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private DesignationRepository designationRepository;
    @Autowired
    private HolidayRepository holidayRepository;
    @Autowired
    private PerformanceReviewRepository performanceReviewRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    public DashboardResponse getDashboard() {
        long totalEmployees = employeeRepository.count();
        long activeEmployees = employeeRepository.countByIsActive(true);
        long inactiveEmployees = employeeRepository.countByIsActive(false);
        long totalManagers = employeeRepository.countByRoleAndIsActive(Role.MANAGER, true);
        long totalAdmins = employeeRepository.countByRoleAndIsActive(Role.ADMIN, true);
        long totalRegular = employeeRepository.countByRoleAndIsActive(Role.EMPLOYEE, true);
        long pendingLeaves = leaveApplicationRepository.countByStatus(LeaveStatus.PENDING);
        long approvedToday = leaveApplicationRepository.findActiveLeavesToday(LeaveStatus.APPROVED, LocalDate.now()).size();
        long totalDepartments = departmentRepository.count();
        long totalDesignations = designationRepository.count();
        Map<String, Long> employeesByDept = new LinkedHashMap<>();
        List<Object[]> deptCounts = employeeRepository.countActiveByDepartment();
        for (Object[] row : deptCounts) {
            employeesByDept.put((String) row[0], (Long) row[1]);
        }
        return DashboardResponse.builder()
                .totalEmployees(totalEmployees).activeEmployees(activeEmployees)
                .inactiveEmployees(inactiveEmployees).totalManagers(totalManagers)
                .totalAdmins(totalAdmins).totalRegularEmployees(totalRegular)
                .pendingLeaves(pendingLeaves).approvedLeavesToday(approvedToday)
                .totalDepartments(totalDepartments).totalDesignations(totalDesignations)
                .employeesByDepartment(employeesByDept).build();
    }

    public List<LeaveReportResponse> getLeaveReport(Integer year) {
        int reportYear = year != null ? year : LocalDate.now().getYear();
        List<LeaveBalance> allBalances = leaveBalanceRepository.findAll();

        return allBalances.stream()
                .filter(b -> b.getYear().equals(reportYear))
                .map(b -> LeaveReportResponse.builder()
                        .employeeCode(b.getEmployee().getEmployeeCode())
                        .employeeName(b.getEmployee().getFirstName() + " " + b.getEmployee().getLastName())
                        .departmentName(b.getEmployee().getDepartment() != null ? b.getEmployee().getDepartment().getDepartmentName() : "N/A")
                        .leaveTypeName(b.getLeaveType().getLeaveTypeName())
                        .totalLeaves(b.getTotalLeaves())
                        .usedLeaves(b.getUsedLeaves())
                        .availableBalance(b.getAvailableBalance())
                        .year(b.getYear())
                        .build())
                .collect(Collectors.toList());
    }

    public List<LeaveReportResponse> getLeaveReportByDepartment(Integer departmentId, Integer year) {
        int reportYear = year != null ? year : LocalDate.now().getYear();
        List<LeaveBalance> allBalances = leaveBalanceRepository.findAll();

        return allBalances.stream()
                .filter(b -> b.getYear().equals(reportYear))
                .filter(b -> b.getEmployee().getDepartment() != null && b.getEmployee().getDepartment().getDepartmentId().equals(departmentId))
                .map(b -> LeaveReportResponse.builder()
                        .employeeCode(b.getEmployee().getEmployeeCode())
                        .employeeName(b.getEmployee().getFirstName() + " " + b.getEmployee().getLastName())
                        .departmentName(b.getEmployee().getDepartment().getDepartmentName())
                        .leaveTypeName(b.getLeaveType().getLeaveTypeName())
                        .totalLeaves(b.getTotalLeaves())
                        .usedLeaves(b.getUsedLeaves())
                        .availableBalance(b.getAvailableBalance())
                        .year(b.getYear())
                        .build())
                .collect(Collectors.toList());
    }

    public List<LeaveReportResponse> getLeaveReportByEmployee(String employeeCode, Integer year) {
        int reportYear = year != null ? year : LocalDate.now().getYear();
        List<LeaveBalance> allBalances = leaveBalanceRepository.findAll();

        return allBalances.stream()
                .filter(b -> b.getYear().equals(reportYear))
                .filter(b -> b.getEmployee().getEmployeeCode().equals(employeeCode))
                .map(b -> LeaveReportResponse.builder()
                        .employeeCode(b.getEmployee().getEmployeeCode())
                        .employeeName(b.getEmployee().getFirstName() + " " + b.getEmployee().getLastName())
                        .departmentName(b.getEmployee().getDepartment() != null ? b.getEmployee().getDepartment().getDepartmentName() : "N/A")
                        .leaveTypeName(b.getLeaveType().getLeaveTypeName())
                        .totalLeaves(b.getTotalLeaves()).usedLeaves(b.getUsedLeaves())
                        .availableBalance(b.getAvailableBalance()).year(b.getYear()).build())
                .collect(Collectors.toList());
    }

    public EmployeeDashboardResponse getEmployeeDashboard(String email) {
        Employee employee = employeeRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with email: " + email));

        int currentYear = LocalDate.now().getYear();

        List<LeaveBalance> balances = leaveBalanceRepository
                .findByEmployee_EmployeeIdAndYear(employee.getEmployeeId(), currentYear);
        List<EmployeeDashboardResponse.LeaveBalanceSummary> leaveBalanceSummaries = balances.stream()
                .map(b -> EmployeeDashboardResponse.LeaveBalanceSummary.builder()
                        .leaveTypeName(b.getLeaveType().getLeaveTypeName())
                        .totalLeaves(b.getTotalLeaves())
                        .usedLeaves(b.getUsedLeaves())
                        .availableBalance(b.getAvailableBalance())
                        .build())
                .collect(Collectors.toList());

        long pendingLeaves = leaveApplicationRepository.countByEmployee_EmployeeIdAndStatus(
                employee.getEmployeeId(), LeaveStatus.PENDING);
        long approvedLeaves = leaveApplicationRepository.countByEmployee_EmployeeIdAndStatus(
                employee.getEmployeeId(), LeaveStatus.APPROVED);

        LocalDate today = LocalDate.now();
        List<Holiday> holidays = holidayRepository.findByHolidayDateBetween(today, today.plusDays(30));
        List<EmployeeDashboardResponse.UpcomingHolidaySummary> upcomingHolidays = holidays.stream()
                .map(h -> EmployeeDashboardResponse.UpcomingHolidaySummary.builder()
                        .holidayName(h.getHolidayName())
                        .holidayDate(h.getHolidayDate())
                        .description(h.getDescription())
                        .build())
                .collect(Collectors.toList());

        long unreadNotifications = notificationRepository
                .countByRecipient_EmployeeIdAndIsRead(employee.getEmployeeId(), false);

        return EmployeeDashboardResponse.builder()
                .employeeName(employee.getFirstName() + " " + employee.getLastName())
                .employeeCode(employee.getEmployeeCode())
                .departmentName(employee.getDepartment() != null ? employee.getDepartment().getDepartmentName() : "N/A")
                .designationTitle(employee.getDesignation() != null ? employee.getDesignation().getDesignationName() : "N/A")
                .pendingLeaveRequests(pendingLeaves)
                .approvedLeaves(approvedLeaves)
                .unreadNotifications(unreadNotifications)
                .leaveBalances(leaveBalanceSummaries)
                .upcomingHolidays(upcomingHolidays)
                .build();
    }

    public EmployeeReportResponse getEmployeeReport() {
        long totalEmployees = employeeRepository.count();
        long activeEmployees = employeeRepository.countByIsActive(true);
        long inactiveEmployees = employeeRepository.countByIsActive(false);

        Map<String, Long> headcountByDept = new LinkedHashMap<>();
        for (Object[] row : employeeRepository.countActiveByDepartment()) {
            headcountByDept.put((String) row[0], (Long) row[1]);
        }

        Map<String, Long> headcountByRole = new LinkedHashMap<>();
        for (Object[] row : employeeRepository.countActiveByRole()) {
            headcountByRole.put(row[0].toString(), (Long) row[1]);
        }

        List<EmployeeReportResponse.JoiningTrend> joiningTrends = new ArrayList<>();
        for (Object[] row : employeeRepository.getJoiningTrends()) {
            int year = ((Number) row[0]).intValue();
            int month = ((Number) row[1]).intValue();
            long count = ((Number) row[2]).longValue();
            joiningTrends.add(EmployeeReportResponse.JoiningTrend.builder()
                    .period(String.format("%d-%02d", year, month))
                    .count(count)
                    .build());
        }

        List<Employee> activeEmps = employeeRepository.findAll().stream()
                .filter(Employee::getIsActive)
                .filter(e -> e.getJoiningDate() != null)
                .toList();
        double avgTenure = 0;
        if (!activeEmps.isEmpty()) {
            LocalDate today = LocalDate.now();
            long totalMonths = activeEmps.stream()
                    .mapToLong(e -> ChronoUnit.MONTHS.between(e.getJoiningDate(), today))
                    .sum();
            avgTenure = Math.round((double) totalMonths / activeEmps.size() * 100.0) / 100.0;
        }

        return EmployeeReportResponse.builder()
                .totalEmployees(totalEmployees)
                .activeEmployees(activeEmployees)
                .inactiveEmployees(inactiveEmployees)
                .headcountByDepartment(headcountByDept)
                .headcountByRole(headcountByRole)
                .joiningTrends(joiningTrends)
                .averageTenureMonths(avgTenure)
                .build();
    }
}
