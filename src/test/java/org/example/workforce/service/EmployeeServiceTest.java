package org.example.workforce.service;

import org.example.workforce.dto.RegisterEmployeeRequest;
import org.example.workforce.dto.UpdateEmployeeRequest;
import org.example.workforce.exception.*;
import org.example.workforce.model.*;
import org.example.workforce.model.enums.Role;
import org.example.workforce.repository.*;
import org.example.workforce.model.ActivityLog;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private DesignationRepository designationRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ActivityLogRepository activityLogRepository;

    @InjectMocks
    private EmployeeService employeeService;

    private Employee employee;
    private Department department;
    private Designation designation;
    private RegisterEmployeeRequest registerRequest;

    @BeforeEach
    void setUp() {
        department = Department.builder()
                .departmentId(1)
                .departmentName("Engineering")
                .isActive(true)
                .build();

        designation = Designation.builder()
                .designationId(1)
                .designationName("Software Engineer")
                .isActive(true)
                .build();

        employee = Employee.builder()
                .employeeId(1)
                .email("employee@test.com")
                .firstName("John")
                .lastName("Doe")
                .employeeCode("EMP001")
                .role(Role.EMPLOYEE)
                .department(department)
                .designation(designation)
                .isActive(true)
                .build();

        registerRequest = new RegisterEmployeeRequest();
        registerRequest.setEmail("newemployee@test.com");
        registerRequest.setPassword("password123");
        registerRequest.setFirstName("Jane");
        registerRequest.setLastName("Smith");
        registerRequest.setRole("EMPLOYEE");
        registerRequest.setDepartmentId(1);
        registerRequest.setDesignationId(1);
        registerRequest.setJoiningDate(LocalDate.now());
    }

    @Test
    void testRegisterEmployee_EmailAlreadyExists() {

        when(employeeRepository.existsByEmail("newemployee@test.com")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> {
            employeeService.registerEmployee(registerRequest);
        });
    }

    @Test
    void testRegisterEmployee_DepartmentNotFound() {

        when(employeeRepository.existsByEmail(anyString())).thenReturn(false);
        when(employeeRepository.findLatestEmployeeCodeByPrefix(anyString())).thenReturn(Optional.empty());
        when(departmentRepository.findById(1)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            employeeService.registerEmployee(registerRequest);
        });
    }

    @Test
    void testUpdateEmployee_Success() {

        UpdateEmployeeRequest updateRequest = new UpdateEmployeeRequest();
        updateRequest.setFirstName("Updated");
        updateRequest.setLastName("Name");
        updateRequest.setDepartmentId(1);
        updateRequest.setDesignationId(1);

        Employee admin = Employee.builder()
                .employeeId(2)
                .email("admin@test.com")
                .role(Role.ADMIN)
                .build();

        when(employeeRepository.findByEmployeeCode("EMP001")).thenReturn(Optional.of(employee));
        when(employeeRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(departmentRepository.findById(1)).thenReturn(Optional.of(department));
        when(designationRepository.findById(1)).thenReturn(Optional.of(designation));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(i -> i.getArgument(0));

        var result = employeeService.updateEmployeeByAdmin("EMP001", updateRequest, "admin@test.com");

        assertNotNull(result);
        assertEquals("Updated", result.getFirstName());
        assertEquals("Name", result.getLastName());
        verify(employeeRepository, times(1)).save(employee);
    }

    @Test
    void testGetEmployee_NotFound() {

        when(employeeRepository.findByEmployeeCode("INVALID")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            employeeService.getEmployeeByCode("INVALID");
        });
    }

    @Test
    void testActivateEmployee_Success() {

        Employee admin = Employee.builder()
                .employeeId(2)
                .email("admin@test.com")
                .role(Role.ADMIN)
                .build();

        employee.setIsActive(false);
        when(employeeRepository.findByEmployeeCode("EMP001")).thenReturn(Optional.of(employee));
        when(employeeRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(i -> i.getArgument(0));

        var result = employeeService.activateEmployee("EMP001", "admin@test.com");

        assertTrue(result.getIsActive());
        verify(employeeRepository, times(1)).save(employee);
    }

    @Test
    void testDeactivateEmployee_Success() {

        Employee admin = Employee.builder()
                .employeeId(2)
                .email("admin@test.com")
                .role(Role.ADMIN)
                .build();

        when(employeeRepository.findByEmployeeCode("EMP001")).thenReturn(Optional.of(employee));
        when(employeeRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(i -> i.getArgument(0));
        when(activityLogRepository.save(any(ActivityLog.class))).thenAnswer(i -> i.getArgument(0));

        var result = employeeService.deactivateEmployee("EMP001", "admin@test.com");

        assertFalse(result.getIsActive());
        verify(employeeRepository, times(1)).save(employee);
    }

    @Test
    void testGetEmployees_WithFilters() {

        Pageable pageable = PageRequest.of(0, 10);
        Page<Employee> employeePage = new PageImpl<>(Collections.singletonList(employee));
        when(employeeRepository.searchByKeyword(anyString(), eq(pageable)))
                .thenReturn(employeePage);

        var result = employeeService.getEmployees("John", null, null, null, pageable);

        assertNotNull(result);
        assertEquals(1, result.getContent().size());
        verify(employeeRepository, times(1)).searchByKeyword(anyString(), eq(pageable));
    }
}
