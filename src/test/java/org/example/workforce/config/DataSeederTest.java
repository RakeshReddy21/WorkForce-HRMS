package org.example.workforce.config;
import org.example.workforce.model.Department;
import org.example.workforce.model.Designation;
import org.example.workforce.model.Employee;
import org.example.workforce.model.enums.Role;
import org.example.workforce.repository.DepartmentRepository;
import org.example.workforce.repository.DesignationRepository;
import org.example.workforce.repository.EmployeeRepository;
import org.example.workforce.repository.LeaveTypeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class DataSeederTest {
    @Mock
    private DepartmentRepository departmentRepository;
    @Mock
    private DesignationRepository designationRepository;
    @Mock
    private LeaveTypeRepository leaveTypeRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    private DataSeeder dataSeeder = new DataSeeder();
    @Test
    void testInitDatabase_SeedsDataWhenEmpty() throws Exception {
        when(departmentRepository.count()).thenReturn(0L);
        when(designationRepository.count()).thenReturn(0L);
        when(leaveTypeRepository.count()).thenReturn(0L);
        CommandLineRunner runner = dataSeeder.initDatabase(departmentRepository, designationRepository, leaveTypeRepository);
        runner.run();
        verify(departmentRepository, times(1)).saveAll(anyList());
        verify(designationRepository, times(1)).saveAll(anyList());
        verify(leaveTypeRepository, times(1)).saveAll(anyList());
    }
    @Test
    void testInitDatabase_DoesNotSeedDataWhenNotEmpty() throws Exception {
        when(departmentRepository.count()).thenReturn(5L);
        when(designationRepository.count()).thenReturn(5L);
        when(leaveTypeRepository.count()).thenReturn(5L);
        CommandLineRunner runner = dataSeeder.initDatabase(departmentRepository, designationRepository, leaveTypeRepository);
        runner.run();
        verify(departmentRepository, never()).saveAll(anyList());
        verify(designationRepository, never()).saveAll(anyList());
        verify(leaveTypeRepository, never()).saveAll(anyList());
    }

    @Test
    void testInitDatabase_SeedsOnlyEmptyRepositories() throws Exception {
        when(departmentRepository.count()).thenReturn(0L);
        when(designationRepository.count()).thenReturn(5L);
        when(leaveTypeRepository.count()).thenReturn(0L);
        CommandLineRunner runner = dataSeeder.initDatabase(departmentRepository, designationRepository, leaveTypeRepository);
        runner.run();
        verify(departmentRepository, times(1)).saveAll(anyList());
        verify(designationRepository, never()).saveAll(anyList());
        verify(leaveTypeRepository, times(1)).saveAll(anyList());
    }

    @Test
    void testInitDatabase_SeedsCorrectDepartments() throws Exception {
        when(departmentRepository.count()).thenReturn(0L);
        when(designationRepository.count()).thenReturn(5L);
        when(leaveTypeRepository.count()).thenReturn(5L);
        ArgumentCaptor<List<Department>> deptCaptor = ArgumentCaptor.forClass(List.class);
        CommandLineRunner runner = dataSeeder.initDatabase(departmentRepository, designationRepository, leaveTypeRepository);
        runner.run();
        verify(departmentRepository, times(1)).saveAll(deptCaptor.capture());
        List<Department> departments = deptCaptor.getValue();
        assertEquals(4, departments.size());
        assertTrue(departments.stream().anyMatch(d -> "IT".equals(d.getDepartmentName())));
        assertTrue(departments.stream().anyMatch(d -> "HR".equals(d.getDepartmentName())));
        assertTrue(departments.stream().anyMatch(d -> "Finance".equals(d.getDepartmentName())));
        assertTrue(departments.stream().anyMatch(d -> "Marketing".equals(d.getDepartmentName())));
    }

    @Test
    void testInitDatabase_SeedsCorrectDesignations() throws Exception {
        when(departmentRepository.count()).thenReturn(5L);
        when(designationRepository.count()).thenReturn(0L);
        when(leaveTypeRepository.count()).thenReturn(5L);
        ArgumentCaptor<List<Designation>> desigCaptor = ArgumentCaptor.forClass(List.class);
        CommandLineRunner runner = dataSeeder.initDatabase(departmentRepository, designationRepository, leaveTypeRepository);
        runner.run();
        verify(designationRepository, times(1)).saveAll(desigCaptor.capture());
        List<Designation> designations = desigCaptor.getValue();
        assertEquals(4, designations.size());
        assertTrue(designations.stream().anyMatch(d -> "Software Engineer".equals(d.getDesignationName())));
        assertTrue(designations.stream().anyMatch(d -> "HR Manager".equals(d.getDesignationName())));
        assertTrue(designations.stream().anyMatch(d -> "Accountant".equals(d.getDesignationName())));
        assertTrue(designations.stream().anyMatch(d -> "Marketing Executive".equals(d.getDesignationName())));
    }

    @Test
    void testInitAdmin_CreatesAdminWhenNotExists() throws Exception {
        when(employeeRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(passwordEncoder.encode(any(CharSequence.class))).thenReturn("encodedPassword");
        ArgumentCaptor<Employee> employeeCaptor = ArgumentCaptor.forClass(Employee.class);
        CommandLineRunner runner = dataSeeder.initAdmin(employeeRepository, passwordEncoder);
        runner.run();
        verify(employeeRepository, times(1)).save(employeeCaptor.capture());
        Employee admin = employeeCaptor.getValue();
        assertEquals("ADM001", admin.getEmployeeCode());
        assertEquals("System", admin.getFirstName());
        assertEquals("Admin", admin.getLastName());
        assertEquals("admin@workforce.com", admin.getEmail());
        assertEquals(Role.ADMIN, admin.getRole());
        assertTrue(admin.getIsActive());
        verify(passwordEncoder, times(1)).encode("Admin@123");
    }

    @Test
    void testInitAdmin_DoesNotCreateAdminWhenExists() throws Exception {
        when(employeeRepository.existsByRole(Role.ADMIN)).thenReturn(true);
        CommandLineRunner runner = dataSeeder.initAdmin(employeeRepository, passwordEncoder);
        runner.run();
        verify(employeeRepository, never()).save(any(Employee.class));
        verify(passwordEncoder, never()).encode(any(CharSequence.class));
    }
}
