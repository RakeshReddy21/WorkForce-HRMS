package org.example.workforce.service;

import org.example.workforce.dto.EmployeeProfileResponse;
import org.example.workforce.dto.RegisterEmployeeRequest;
import org.example.workforce.dto.UpdateEmployeeRequest;
import org.example.workforce.dto.UpdateProfileRequest;
import org.example.workforce.exception.*;
import org.example.workforce.model.*;
import org.example.workforce.model.enums.*;
import org.example.workforce.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.example.workforce.dto.ChangePasswordRequest;

@Service
@Transactional
public class EmployeeService {
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private DepartmentRepository departmentRepository;
    @Autowired
    private DesignationRepository designationRepository;
    @Autowired
    private ActivityLogRepository activityLogRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private EmailService emailService;

    public Employee registerEmployee(RegisterEmployeeRequest request) {
        if (employeeRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateResourceException("Email already exists: " + request.getEmail());
        }
        Role role = Role.EMPLOYEE;
        if (request.getRole() != null && !request.getRole().isBlank()) {
            try {
                role = Role.valueOf(request.getRole().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid role value: " + request.getRole() + ". Allowed values: EMPLOYEE, MANAGER, ADMIN");
            }
        }
        String employeeCode = generateEmployeeCode(role);

        boolean enforce2FA = (role == Role.EMPLOYEE || role == Role.MANAGER);

        Employee employee = Employee.builder()
                .firstName(request.getFirstName()).lastName(request.getLastName())
                .email(request.getEmail()).passwordHash(passwordEncoder.encode(request.getPassword()))
                .employeeCode(employeeCode).phone(request.getPhone()).dateOfBirth(request.getDateOfBirth())
                .address(request.getAddress()).emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone()).joiningDate(request.getJoiningDate())
                .salary(request.getSalary()).role(role).twoFactorEnabled(enforce2FA).build();
        if (request.getGender() != null && !request.getGender().isBlank()) {
            try {
                employee.setGender(Gender.valueOf(request.getGender().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid gender value: " + request.getGender() + ". Allowed values: MALE, FEMALE, OTHER");
            }
        }
        if (request.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + request.getDepartmentId()));
            employee.setDepartment(dept);
        }
        if (request.getDesignationId() != null) {
            Designation desig = designationRepository.findById(request.getDesignationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Designation not found with id: " + request.getDesignationId()));
            employee.setDesignation(desig);
        }
        if (role == Role.EMPLOYEE) {
            if (request.getManagerCode() == null || request.getManagerCode().isBlank()) {
                throw new BadRequestException("Manager code is required for EMPLOYEE role. Provide a valid manager code (e.g. MG001).");
            }
            Employee manager = employeeRepository.findByEmployeeCode(request.getManagerCode())
                    .orElseThrow(() -> new ResourceNotFoundException("Manager not found with code: " + request.getManagerCode()));
            if (manager.getRole() != Role.MANAGER && manager.getRole() != Role.ADMIN) {
                throw new BadRequestException("Employee with code " + request.getManagerCode() + " is not a MANAGER or ADMIN. Only managers/admins can be assigned as a manager.");
            }
            employee.setManager(manager);
        }
        Employee savedEmployee = employeeRepository.save(employee);

        // Send welcome email with credentials
        try {
            emailService.sendWelcomeEmail(
                    savedEmployee.getEmail(),
                    savedEmployee.getFirstName() + " " + savedEmployee.getLastName(),
                    savedEmployee.getEmployeeCode(),
                    request.getPassword(),
                    savedEmployee.getRole().name()
            );
        } catch (Exception e) {
            // Don't fail registration if email fails
        }

        return savedEmployee;
    }

    private String generateEmployeeCode(Role role) {
        String prefix = switch (role) {
            case ADMIN -> "ADM";
            case MANAGER -> "MG";
            case EMPLOYEE -> "EMP";
        };
        var latestCode = employeeRepository.findLatestEmployeeCodeByPrefix(prefix);
        int nextNumber = 1;
        if (latestCode.isPresent()) {
            String numericPart = latestCode.get().substring(prefix.length());
            nextNumber = Integer.parseInt(numericPart) + 1;
        }
        return String.format("%s%03d", prefix, nextNumber);
    }

    public Employee getEmployeeByEmail(String email) {
        return employeeRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with email: " + email));
    }

    public Employee updateProfile(Integer employeeId, UpdateProfileRequest request) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with id: " + employeeId));
        StringBuilder changes = new StringBuilder();
        if (request.getPhone() != null && !request.getPhone().equals(employee.getPhone())) {
            changes.append(String.format("Phone: '%s' -> '%s'; ",
                    employee.getPhone() != null ? employee.getPhone() : "null", request.getPhone()));
            employee.setPhone(request.getPhone());
        }
        if (request.getAddress() != null && !request.getAddress().equals(employee.getAddress())) {
            changes.append(String.format("Address: '%s' -> '%s'; ",
                    employee.getAddress() != null ? employee.getAddress() : "null", request.getAddress()));
            employee.setAddress(request.getAddress());
        }
        if (request.getEmergencyContactName() != null
                && !request.getEmergencyContactName().equals(employee.getEmergencyContactName())) {
            changes.append(String.format("EmergencyContactName: '%s' -> '%s'; ",
                    employee.getEmergencyContactName() != null ? employee.getEmergencyContactName() : "null",
                    request.getEmergencyContactName()));
            employee.setEmergencyContactName(request.getEmergencyContactName());
        }
        if (request.getEmergencyContactPhone() != null
                && !request.getEmergencyContactPhone().equals(employee.getEmergencyContactPhone())) {
            changes.append(String.format("EmergencyContactPhone: '%s' -> '%s'; ",
                    employee.getEmergencyContactPhone() != null ? employee.getEmergencyContactPhone() : "null",
                    request.getEmergencyContactPhone()));
            employee.setEmergencyContactPhone(request.getEmergencyContactPhone());
        }
        Employee savedEmployee = employeeRepository.save(employee);
        if (!changes.isEmpty()) {
            ActivityLog log = ActivityLog.builder()
                    .performedBy(employee)
                    .action("PROFILE_UPDATE")
                    .entityType("EMPLOYEE")
                    .entityId(employeeId)
                    .details(changes.toString())
                    .build();
            activityLogRepository.save(log);
        }
        return savedEmployee;
    }

    @Transactional(readOnly = true)
    public EmployeeProfileResponse getEmployeeProfileByEmail(String email) {
        Employee employee = getEmployeeByEmail(email);
        return mapToProfileResponse(employee);
    }

    public EmployeeProfileResponse updateProfileWithResponse(String email, UpdateProfileRequest request) {
        Employee employee = getEmployeeByEmail(email);
        Employee updatedEmployee = updateProfile(employee.getEmployeeId(), request);
        return mapToProfileResponse(updatedEmployee);
    }

    private EmployeeProfileResponse mapToProfileResponse(Employee employee) {
        EmployeeProfileResponse.ManagerInfo managerInfo = null;
        if (employee.getManager() != null) {
            Employee manager = employee.getManager();
            managerInfo = EmployeeProfileResponse.ManagerInfo.builder()
                    .managerId(manager.getEmployeeId())
                    .managerCode(manager.getEmployeeCode())
                    .managerName(manager.getFirstName() + " " + manager.getLastName())
                    .managerEmail(manager.getEmail())
                    .managerPhone(manager.getPhone())
                    .build();
        }
        return EmployeeProfileResponse.builder()
                .employeeId(employee.getEmployeeId())
                .employeeCode(employee.getEmployeeCode())
                .firstName(employee.getFirstName())
                .lastName(employee.getLastName())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .dateOfBirth(employee.getDateOfBirth())
                .gender(employee.getGender() != null ? employee.getGender().name() : null)
                .address(employee.getAddress())
                .emergencyContactName(employee.getEmergencyContactName())
                .emergencyContactPhone(employee.getEmergencyContactPhone())
                .departmentId(employee.getDepartment() != null ? employee.getDepartment().getDepartmentId() : null)
                .departmentName(employee.getDepartment() != null ? employee.getDepartment().getDepartmentName() : null)
                .designationId(employee.getDesignation() != null ? employee.getDesignation().getDesignationId() : null)
                .designationTitle(employee.getDesignation() != null ? employee.getDesignation().getDesignationName() : null)
                .joiningDate(employee.getJoiningDate())
                .salary(employee.getSalary())
                .role(employee.getRole().name())
                .isActive(employee.getIsActive())
                .twoFactorEnabled(employee.getTwoFactorEnabled())
                .createdAt(employee.getCreatedAt())
                .updatedAt(employee.getUpdatedAt())
                .manager(managerInfo)
                .build();
    }

    @Transactional(readOnly = true)
    public EmployeeProfileResponse getEmployeeByCode(String employeeCode) {
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with code: " + employeeCode));
        return mapToProfileResponse(employee);
    }

    @Transactional(readOnly = true)
    public Page<EmployeeProfileResponse> getEmployees(String keyword, Integer departmentId, String role, Boolean isActive, Pageable pageable) {
        Page<Employee> employees;
        if (keyword != null && !keyword.isBlank()) {
            employees = employeeRepository.searchByKeyword(keyword.trim(), pageable);
        } else if (departmentId != null) {
            employees = employeeRepository.findByDepartment_DepartmentId(departmentId, pageable);
        } else if (role != null && !role.isBlank()) {
            try {
                Role roleEnum = Role.valueOf(role.toUpperCase());
                employees = employeeRepository.findByRole(roleEnum, pageable);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid role: " + role + ". Allowed: EMPLOYEE, MANAGER, ADMIN");
            }
        } else if (isActive != null) {
            employees = employeeRepository.findByIsActive(isActive, pageable);
        } else {
            employees = employeeRepository.findAll(pageable);
        }
        return employees.map(this::mapToProfileResponse);
    }

    public EmployeeProfileResponse updateEmployeeByAdmin(String employeeCode, UpdateEmployeeRequest request, String adminEmail) {
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with code: " + employeeCode));
        Employee admin = getEmployeeByEmail(adminEmail);
        StringBuilder changes = new StringBuilder();
        if (request.getFirstName() != null && !request.getFirstName().equals(employee.getFirstName())) {
            changes.append("FirstName: '").append(employee.getFirstName()).append("' -> '").append(request.getFirstName()).append("'; ");
            employee.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null && !request.getLastName().equals(employee.getLastName())) {
            changes.append("LastName: '").append(employee.getLastName()).append("' -> '").append(request.getLastName()).append("'; ");
            employee.setLastName(request.getLastName());
        }
        if (request.getEmail() != null && !request.getEmail().equals(employee.getEmail())) {
            if (employeeRepository.existsByEmail(request.getEmail())) {
                throw new DuplicateResourceException("Email already in use: " + request.getEmail());
            }
            changes.append("Email: '").append(employee.getEmail()).append("' -> '").append(request.getEmail()).append("'; ");
            employee.setEmail(request.getEmail());
        }
        if (request.getPhone() != null && !request.getPhone().equals(employee.getPhone())) {
            changes.append("Phone: '").append(employee.getPhone() != null ? employee.getPhone() : "null").append("' -> '").append(request.getPhone()).append("'; ");
            employee.setPhone(request.getPhone());
        }
        if (request.getDateOfBirth() != null && !request.getDateOfBirth().equals(employee.getDateOfBirth())) {
            changes.append("DateOfBirth: '").append(employee.getDateOfBirth() != null ? employee.getDateOfBirth() : "null").append("' -> '").append(request.getDateOfBirth()).append("'; ");
            employee.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getGender() != null && !request.getGender().isBlank()) {
            try {
                Gender newGender = Gender.valueOf(request.getGender().toUpperCase());
                if (employee.getGender() != newGender) {
                    changes.append("Gender: '").append(employee.getGender() != null ? employee.getGender() : "null").append("' -> '").append(newGender).append("'; ");
                }
                employee.setGender(newGender);
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid gender: " + request.getGender() + ". Allowed: MALE, FEMALE, OTHER");
            }
        }
        if (request.getAddress() != null && !request.getAddress().equals(employee.getAddress())) {
            changes.append("Address: '").append(employee.getAddress() != null ? employee.getAddress() : "null").append("' -> '").append(request.getAddress()).append("'; ");
            employee.setAddress(request.getAddress());
        }
        if (request.getEmergencyContactName() != null && !request.getEmergencyContactName().equals(employee.getEmergencyContactName())) {
            changes.append("EmergencyContactName: '").append(employee.getEmergencyContactName() != null ? employee.getEmergencyContactName() : "null").append("' -> '").append(request.getEmergencyContactName()).append("'; ");
            employee.setEmergencyContactName(request.getEmergencyContactName());
        }
        if (request.getEmergencyContactPhone() != null && !request.getEmergencyContactPhone().equals(employee.getEmergencyContactPhone())) {
            changes.append("EmergencyContactPhone: '").append(employee.getEmergencyContactPhone() != null ? employee.getEmergencyContactPhone() : "null").append("' -> '").append(request.getEmergencyContactPhone()).append("'; ");
            employee.setEmergencyContactPhone(request.getEmergencyContactPhone());
        }
        if (request.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Department not found with id: " + request.getDepartmentId()));
            changes.append("Department changed; ");
            employee.setDepartment(dept);
        }
        if (request.getDesignationId() != null) {
            Designation desig = designationRepository.findById(request.getDesignationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Designation not found with id: " + request.getDesignationId()));
            changes.append("Designation changed; ");
            employee.setDesignation(desig);
        }
        if (request.getJoiningDate() != null && !request.getJoiningDate().equals(employee.getJoiningDate())) {
            changes.append("JoiningDate: '").append(employee.getJoiningDate() != null ? employee.getJoiningDate() : "null").append("' -> '").append(request.getJoiningDate()).append("'; ");
            employee.setJoiningDate(request.getJoiningDate());
        }
        if (request.getSalary() != null && !request.getSalary().equals(employee.getSalary())) {
            changes.append("Salary: '").append(employee.getSalary() != null ? employee.getSalary() : "null").append("' -> '").append(request.getSalary()).append("'; ");
            employee.setSalary(request.getSalary());
        }
        if (request.getRole() != null && !request.getRole().isBlank()) {
            try {
                Role newRole = Role.valueOf(request.getRole().toUpperCase());
                changes.append("Role: '").append(employee.getRole()).append("' -> '").append(newRole).append("'; ");
                employee.setRole(newRole);

                if ((newRole == Role.EMPLOYEE || newRole == Role.MANAGER) && !Boolean.TRUE.equals(employee.getTwoFactorEnabled())) {
                    employee.setTwoFactorEnabled(true);
                    changes.append("2FA: auto-enabled (mandatory for ").append(newRole).append("); ");
                }
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid role: " + request.getRole() + ". Allowed: EMPLOYEE, MANAGER, ADMIN");
            }
        }
        Employee saved = employeeRepository.save(employee);
        if (!changes.isEmpty()) {
            activityLogRepository.save(ActivityLog.builder()
                    .performedBy(admin).action("ADMIN_UPDATE_EMPLOYEE").entityType("EMPLOYEE")
                    .entityId(employee.getEmployeeId()).details(changes.toString()).build());
        }
        return mapToProfileResponse(saved);
    }

    public EmployeeProfileResponse deactivateEmployee(String employeeCode, String adminEmail) {
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with code: " + employeeCode));
        if (!employee.getIsActive()) {
            throw new InvalidActionException("Employee is already deactivated");
        }
        employee.setIsActive(false);
        Employee saved = employeeRepository.save(employee);
        Employee admin = getEmployeeByEmail(adminEmail);
        activityLogRepository.save(ActivityLog.builder()
                .performedBy(admin).action("DEACTIVATE_EMPLOYEE").entityType("EMPLOYEE")
                .entityId(employee.getEmployeeId()).details("Deactivated employee: " + employeeCode).build());
        return mapToProfileResponse(saved);
    }

    public EmployeeProfileResponse activateEmployee(String employeeCode, String adminEmail) {
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with code: " + employeeCode));
        if (employee.getIsActive()) {
            throw new InvalidActionException("Employee is already active");
        }
        employee.setIsActive(true);
        Employee saved = employeeRepository.save(employee);
        Employee admin = getEmployeeByEmail(adminEmail);
        activityLogRepository.save(ActivityLog.builder()
                .performedBy(admin).action("ACTIVATE_EMPLOYEE").entityType("EMPLOYEE")
                .entityId(employee.getEmployeeId()).details("Reactivated employee: " + employeeCode).build());
        return mapToProfileResponse(saved);
    }

    public void changePassword(String email, ChangePasswordRequest request) {
        Employee employee = getEmployeeByEmail(email);

        if (!passwordEncoder.matches(request.getCurrentPassword(), employee.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("New password and confirm password do not match");
        }
        if (passwordEncoder.matches(request.getNewPassword(), employee.getPasswordHash())) {
            throw new BadRequestException("New password must be different from the current password");
        }

        employee.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        employeeRepository.save(employee);

        activityLogRepository.save(ActivityLog.builder()
                .performedBy(employee).action("PASSWORD_CHANGE").entityType("EMPLOYEE")
                .entityId(employee.getEmployeeId()).details("Employee changed their own password").build());
    }

    public void forceResetPassword(String employeeCode, String newPassword, String adminEmail) {
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with code: " + employeeCode));
        Employee admin = getEmployeeByEmail(adminEmail);

        employee.setPasswordHash(passwordEncoder.encode(newPassword));
        employeeRepository.save(employee);

        activityLogRepository.save(ActivityLog.builder()
                .performedBy(admin).action("ADMIN_FORCE_RESET_PASSWORD").entityType("EMPLOYEE")
                .entityId(employee.getEmployeeId())
                .details("Admin force-reset password for employee: " + employeeCode).build());
    }

    public void enable2FA(String employeeCode, String adminEmail) {
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with code: " + employeeCode));
        if (Boolean.TRUE.equals(employee.getTwoFactorEnabled())) {
            throw new InvalidActionException("2FA is already enabled for this employee");
        }
        employee.setTwoFactorEnabled(true);
        employeeRepository.save(employee);
        Employee admin = getEmployeeByEmail(adminEmail);
        activityLogRepository.save(ActivityLog.builder()
                .performedBy(admin).action("ENABLE_2FA").entityType("EMPLOYEE")
                .entityId(employee.getEmployeeId())
                .details("Admin enabled 2FA for employee: " + employeeCode).build());
    }

    public void disable2FA(String employeeCode, String adminEmail) {
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with code: " + employeeCode));

        if (employee.getRole() == Role.EMPLOYEE || employee.getRole() == Role.MANAGER) {
            throw new InvalidActionException("Two-factor authentication is mandatory for "
                    + employee.getRole().name() + " role and cannot be disabled.");
        }

        if (!Boolean.TRUE.equals(employee.getTwoFactorEnabled())) {
            throw new InvalidActionException("2FA is already disabled for this employee");
        }
        employee.setTwoFactorEnabled(false);
        employeeRepository.save(employee);
        Employee admin = getEmployeeByEmail(adminEmail);
        activityLogRepository.save(ActivityLog.builder()
                .performedBy(admin).action("DISABLE_2FA").entityType("EMPLOYEE")
                .entityId(employee.getEmployeeId())
                .details("Admin disabled 2FA for employee: " + employeeCode).build());
    }

    public int forceEnable2FAForAll(String adminEmail) {
        Employee admin = getEmployeeByEmail(adminEmail);

        var employees = employeeRepository.findAll();
        int count = 0;
        for (Employee emp : employees) {
            if ((emp.getRole() == Role.EMPLOYEE || emp.getRole() == Role.MANAGER)
                    && !Boolean.TRUE.equals(emp.getTwoFactorEnabled())) {
                emp.setTwoFactorEnabled(true);
                employeeRepository.save(emp);
                count++;
            }
        }
        activityLogRepository.save(ActivityLog.builder()
                .performedBy(admin).action("FORCE_ENABLE_2FA_ALL").entityType("SYSTEM")
                .entityId(admin.getEmployeeId())
                .details("Admin force-enabled 2FA for " + count + " employees/managers").build());
        return count;
    }

    public EmployeeProfileResponse assignManager(String employeeCode, String managerCode, String adminEmail) {
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with code: " + employeeCode));
        Employee manager = employeeRepository.findByEmployeeCode(managerCode)
                .orElseThrow(() -> new ResourceNotFoundException("Manager not found with code: " + managerCode));
        if (employeeCode.equals(managerCode)) {
            throw new BadRequestException("Employee cannot be their own manager");
        }
        if (manager.getRole() != Role.MANAGER && manager.getRole() != Role.ADMIN) {
            throw new BadRequestException(managerCode + " is not a MANAGER or ADMIN. Only managers/admins can be assigned.");
        }
        String oldManager = employee.getManager() != null ? employee.getManager().getEmployeeCode() : "None";
        employee.setManager(manager);
        Employee saved = employeeRepository.save(employee);
        Employee admin = getEmployeeByEmail(adminEmail);
        activityLogRepository.save(ActivityLog.builder()
                .performedBy(admin).action("CHANGE_MANAGER").entityType("EMPLOYEE")
                .entityId(employee.getEmployeeId())
                .details("Manager: '" + oldManager + "' -> '" + managerCode + "' ").build());
        return mapToProfileResponse(saved);
    }
}
