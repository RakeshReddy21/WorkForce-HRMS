package org.example.workforce.repository;

import org.example.workforce.model.Department;
import org.example.workforce.model.Employee;
import org.example.workforce.model.enums.Gender;
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
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=YEAR",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.jpa.show-sql=true"
})
class EmployeeRepositoryTest {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private DepartmentRepository departmentRepository;

    private Department department;
    private Employee employee;

    @BeforeEach
    void setUp() {
        department = Department.builder()
                .departmentName("IT")
                .isActive(true)
                .build();
        department = departmentRepository.save(department);

        employee = Employee.builder()
                .email("test@example.com")
                .employeeCode("EMP001")
                .firstName("John")
                .lastName("Doe")
                .passwordHash("$2a$10$hashedpassword")
                .role(Role.EMPLOYEE)
                .gender(Gender.MALE)
                .isActive(true)
                .joiningDate(LocalDate.now())
                .department(department)
                .build();
    }

    @Test
    void testFindByEmail() {
        employeeRepository.save(employee);
        Optional<Employee> found = employeeRepository.findByEmail("test@example.com");
        assertTrue(found.isPresent());
        assertEquals("test@example.com", found.get().getEmail());
    }

    @Test
    void testFindByEmployeeCode() {
        employeeRepository.save(employee);
        Optional<Employee> found = employeeRepository.findByEmployeeCode("EMP001");
        assertTrue(found.isPresent());
        assertEquals("EMP001", found.get().getEmployeeCode());
    }

    @Test
    void testExistsByEmail() {
        employeeRepository.save(employee);
        assertTrue(employeeRepository.existsByEmail("test@example.com"));
        assertFalse(employeeRepository.existsByEmail("nonexistent@example.com"));
    }

    @Test
    void testExistsByEmployeeCode() {
        employeeRepository.save(employee);
        assertTrue(employeeRepository.existsByEmployeeCode("EMP001"));
        assertFalse(employeeRepository.existsByEmployeeCode("EMP999"));
    }

    @Test
    void testFindByIsActive() {
        employeeRepository.save(employee);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Employee> activeEmployees = employeeRepository.findByIsActive(true, pageable);
        assertTrue(activeEmployees.getTotalElements() > 0);
    }

    @Test
    void testFindByRole() {
        employeeRepository.save(employee);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Employee> employees = employeeRepository.findByRole(Role.EMPLOYEE, pageable);
        assertTrue(employees.getTotalElements() > 0);
    }

    @Test
    void testFindByDepartment() {
        employeeRepository.save(employee);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Employee> employees = employeeRepository.findByDepartment_DepartmentId(department.getDepartmentId(), pageable);
        assertTrue(employees.getTotalElements() > 0);
    }

    @Test
    void testSearchByKeyword() {
        employeeRepository.save(employee);
        Pageable pageable = PageRequest.of(0, 10);
        Page<Employee> results = employeeRepository.searchByKeyword("John", pageable);
        assertTrue(results.getTotalElements() > 0);
    }

    @Test
    void testCountByIsActive() {
        employeeRepository.save(employee);
        long count = employeeRepository.countByIsActive(true);
        assertTrue(count > 0);
    }

    @Test
    void testCountByRole() {
        employeeRepository.save(employee);
        long count = employeeRepository.countByRole(Role.EMPLOYEE);
        assertTrue(count > 0);
    }

    @Test
    void testFindByRoleAndIsActive() {
        employeeRepository.save(employee);
        List<Employee> employees = employeeRepository.findByRoleAndIsActive(Role.EMPLOYEE, true);
        assertFalse(employees.isEmpty());
    }
}

