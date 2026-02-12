package org.example.workforce.service;
import org.example.workforce.dto.EmployeeProfileResponse;
import org.example.workforce.dto.RegisterEmployeeRequest;
import org.example.workforce.dto.UpdateEmployeeRequest;
import org.example.workforce.dto.UpdateProfileRequest;
import org.example.workforce.model.*;
import org.example.workforce.model.enums.*;
import org.example.workforce.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private BCryptPasswordEncoder passwordEncoder;
    public Employee registerEmployee(RegisterEmployeeRequest request) {
        if (employeeRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already exists: " + request.getEmail());
        }        Role role = Role.EMPLOYEE;
        if (request.getRole() != null && !request.getRole().isBlank()) {
            try {
                role = Role.valueOf(request.getRole().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid role value: " + request.getRole() + ". Allowed values: EMPLOYEE, MANAGER, ADMIN");
            }
        }
        String employeeCode = generateEmployeeCode(role);
        Employee employee = Employee.builder().firstName(request.getFirstName()).lastName(request.getLastName())
                .email(request.getEmail()).passwordHash(passwordEncoder.encode(request.getPassword()))
                .employeeCode(employeeCode).phone(request.getPhone()).dateOfBirth(request.getDateOfBirth())
                .address(request.getAddress()).emergencyContactName(request.getEmergencyContactName())
                .emergencyContactPhone(request.getEmergencyContactPhone()).joiningDate(request.getJoiningDate())
                .salary(request.getSalary()).role(role).build();
        if (request.getGender() != null && !request.getGender().isBlank()) {
            try {
                employee.setGender(Gender.valueOf(request.getGender().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid gender value: " + request.getGender() + ". Allowed values: MALE, FEMALE, OTHER");
            }
        }
        if (request.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Department not found: " + request.getDepartmentId()));
            employee.setDepartment(dept);
        }
        if (request.getDesignationId() != null) {
            Designation desig = designationRepository.findById(request.getDesignationId())
                    .orElseThrow(() -> new RuntimeException("Designation not found: " + request.getDesignationId()));
            employee.setDesignation(desig);
        }
        if (role == Role.EMPLOYEE) {
            if (request.getManagerCode() == null || request.getManagerCode().isBlank()) {
                throw new RuntimeException("Manager code is required for EMPLOYEE role. Provide a valid manager code (e.g. MG001).");
            }
            Employee manager = employeeRepository.findByEmployeeCode(request.getManagerCode())
                    .orElseThrow(() -> new RuntimeException("Manager not found with code: " + request.getManagerCode()));
            if (manager.getRole() != Role.MANAGER && manager.getRole() != Role.ADMIN) {
                throw new RuntimeException("Employee with code " + request.getManagerCode() + " is not a MANAGER or ADMIN. Only managers/admins can be assigned as a manager.");
            }
            employee.setManager(manager);
        }
        return employeeRepository.save(employee);
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
        return employeeRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("Employee not found with email: " + email));
    }
    public Employee updateProfile(Integer employeeId, UpdateProfileRequest request) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new RuntimeException("Employee not found with id: " + employeeId));
        StringBuilder changes = new StringBuilder();
        if (request.getPhone() != null && !request.getPhone().equals(employee.getPhone())) {
            changes.append(String.format("Phone: '%s' -> '%s'; ", employee.getPhone() != null ? employee.getPhone() : "null", request.getPhone()));
            employee.setPhone(request.getPhone());
        }
        if (request.getAddress() != null && !request.getAddress().equals(employee.getAddress())) {
            changes.append(String.format("Address: '%s' -> '%s'; ", employee.getAddress() != null ? employee.getAddress() : "null", request.getAddress()));
            employee.setAddress(request.getAddress());
        }
        if (request.getEmergencyContactName() != null
                && !request.getEmergencyContactName().equals(employee.getEmergencyContactName())) {
            changes.append(String.format("EmergencyContactName: '%s' -> '%s'; ", employee.getEmergencyContactName() != null ? employee.getEmergencyContactName() : "null", request.getEmergencyContactName()));
            employee.setEmergencyContactName(request.getEmergencyContactName());
        }
        if (request.getEmergencyContactPhone() != null
                && !request.getEmergencyContactPhone().equals(employee.getEmergencyContactPhone())) {
            changes.append(String.format("EmergencyContactPhone: '%s' -> '%s'; ", employee.getEmergencyContactPhone() != null ? employee.getEmergencyContactPhone() : "null", request.getEmergencyContactPhone()));
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
                .departmentName(employee.getDepartment() != null ? employee.getDepartment().getDepartmentName() : null)
                .designationTitle(employee.getDesignation() != null ? employee.getDesignation().getDesignationName() : null)
                .joiningDate(employee.getJoiningDate())
                .salary(employee.getSalary())
                .role(employee.getRole().name())
                .isActive(employee.getIsActive())
                .createdAt(employee.getCreatedAt())
                .updatedAt(employee.getUpdatedAt())
                .manager(managerInfo)
                .build();
    }
    @Transactional(readOnly = true)
    public EmployeeProfileResponse getEmployeeByCode(String employeeCode){
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode).orElseThrow(()->new RuntimeException("Employee not found with the code: " + employeeCode));
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
                throw new RuntimeException("Invalid role: " + role + ". Allowed: EMPLOYEE, MANAGER, ADMIN");
            }
        } else if (isActive != null) {
            employees = employeeRepository.findByIsActive(isActive, pageable);
        } else {
            employees = employeeRepository.findAll(pageable);
        }
        return employees.map(this::mapToProfileResponse);
    }
    public EmployeeProfileResponse updateEmployeeByAdmin(String employeeCode, UpdateEmployeeRequest request, String adminEmail) {
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode).orElseThrow(() -> new RuntimeException("Employee not found with code: " + employeeCode));
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
                throw new RuntimeException("Email already in use: " + request.getEmail());
            }
            changes.append("Email: '").append(employee.getEmail()).append("' -> '").append(request.getEmail()).append("'; ");
            employee.setEmail(request.getEmail());
        }
        if (request.getPhone() != null) {
            employee.setPhone(request.getPhone());
        }
        if (request.getDateOfBirth() != null) {
            employee.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getGender() != null && !request.getGender().isBlank()) {
            try {
                employee.setGender(Gender.valueOf(request.getGender().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid gender: " + request.getGender());
            }
        }
        if (request.getAddress() != null) {
            employee.setAddress(request.getAddress());
        }
        if (request.getEmergencyContactName() != null) {
            employee.setEmergencyContactName(request.getEmergencyContactName());
        }
        if (request.getEmergencyContactPhone() != null) {
            employee.setEmergencyContactPhone(request.getEmergencyContactPhone());
        }
        if (request.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(request.getDepartmentId()).orElseThrow(() -> new RuntimeException("Department not found: " + request.getDepartmentId()));
            changes.append("Department changed; ");
            employee.setDepartment(dept);
        }
        if (request.getDesignationId() != null) {
            Designation desig = designationRepository.findById(request.getDesignationId()).orElseThrow(() -> new RuntimeException("Designation not found: " + request.getDesignationId()));
            changes.append("Designation changed; ");
            employee.setDesignation(desig);
        }
        if (request.getJoiningDate() != null) {
            employee.setJoiningDate(request.getJoiningDate());
        }
        if (request.getSalary() != null) {
            employee.setSalary(request.getSalary());
        }
        if (request.getRole() != null && !request.getRole().isBlank()) {
            try {
                Role newRole = Role.valueOf(request.getRole().toUpperCase());
                changes.append("Role: '").append(employee.getRole()).append("' -> '").append(newRole).append("'; ");
                employee.setRole(newRole);
            } catch (IllegalArgumentException e) {
                throw new RuntimeException("Invalid role: " + request.getRole());
            }
        }
        Employee saved = employeeRepository.save(employee);
        if (!changes.isEmpty()) {
            activityLogRepository.save(ActivityLog.builder().performedBy(admin).action("ADMIN_UPDATE_EMPLOYEE").entityType("EMPLOYEE").entityId(employee.getEmployeeId()).details(changes.toString()).build());
        }
        return mapToProfileResponse(saved);
    }
    public EmployeeProfileResponse deactivateEmployee(String employeeCode, String adminEmail) {
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode).orElseThrow(() -> new RuntimeException("Employee not found with code: " + employeeCode));
        if (!employee.getIsActive()) {
            throw new RuntimeException("Employee is already deactivated");
        }
        employee.setIsActive(false);
        Employee saved = employeeRepository.save(employee);
        Employee admin = getEmployeeByEmail(adminEmail);
        activityLogRepository.save(ActivityLog.builder().performedBy(admin).action("DEACTIVATE_EMPLOYEE").entityType("EMPLOYEE").entityId(employee.getEmployeeId()).details("Deactivated employee: " + employeeCode).build());
        return mapToProfileResponse(saved);
    }
    public EmployeeProfileResponse activateEmployee(String employeeCode, String adminEmail){
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode).orElseThrow(() -> new RuntimeException("Employee not found with code: " + employeeCode));
        if(employee.getIsActive()){
            throw new RuntimeException("Employee is already active");
        }
        employee.setIsActive(true);
        Employee saved = employeeRepository.save(employee);
        Employee admin = getEmployeeByEmail(adminEmail);
        activityLogRepository.save(ActivityLog.builder().performedBy(admin).action("ACTIVATE_EMPLOYEE").entityType("EMPLOYEE").entityId(employee.getEmployeeId()).details("Reactivated employee: " + employeeCode).build());
        return mapToProfileResponse(saved);
    }
    public EmployeeProfileResponse assignManager(String employeeCode, String managerCode, String adminEmail){
        Employee employee = employeeRepository.findByEmployeeCode(employeeCode).orElseThrow(() -> new RuntimeException("Employee not found with code: " + employeeCode));
        Employee manager = employeeRepository.findByEmployeeCode(managerCode).orElseThrow(() -> new RuntimeException("Manager not found with code: " + managerCode));
        if(employeeCode.equals(managerCode)){
            throw new RuntimeException("Employee cannot be their own manager");
        }
        if(manager.getRole() != Role.MANAGER && manager.getRole() != Role.ADMIN){
            throw new RuntimeException(managerCode + " is not a manager or not a admin");
        }
        String oldManager = employee.getManager() != null ? employee.getManager().getEmployeeCode() : "None";
        employee.setManager(manager);
        Employee saved = employeeRepository.save(employee);
        Employee admin = getEmployeeByEmail(adminEmail);
        activityLogRepository.save(ActivityLog.builder().performedBy(admin).action("CHANGE_MANAGER").entityType("EMPLOYEE").entityId(employee.getEmployeeId()).details("Manager: '" + oldManager + "' -> '" + managerCode + "' ").build());
        return mapToProfileResponse(saved);
    }
}