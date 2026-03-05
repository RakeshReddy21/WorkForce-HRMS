package org.example.workforce.service;

import org.example.workforce.dto.DepartmentRequest;
import org.example.workforce.exception.BadRequestException;
import org.example.workforce.exception.DuplicateResourceException;
import org.example.workforce.exception.InvalidActionException;
import org.example.workforce.exception.ResourceNotFoundException;
import org.example.workforce.model.Department;
import org.example.workforce.repository.DepartmentRepository;
import org.example.workforce.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DepartmentServiceTest {

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private DepartmentService departmentService;

    private Department department;
    private DepartmentRequest departmentRequest;

    @BeforeEach
    void setUp() {
        department = Department.builder()
                .departmentId(1)
                .departmentName("Engineering")
                .description("Software Engineering Department")
                .isActive(true)
                .build();

        departmentRequest = new DepartmentRequest("Engineering", "Software Engineering Department");
    }

    @Test
    void createDepartment_Success() {
        when(departmentRepository.existsByDepartmentName("Engineering")).thenReturn(false);
        when(departmentRepository.save(any(Department.class))).thenReturn(department);

        Department result = departmentService.createDepartment(departmentRequest);

        assertNotNull(result);
        assertEquals("Engineering", result.getDepartmentName());
        assertEquals("Software Engineering Department", result.getDescription());
        verify(departmentRepository).save(any(Department.class));
    }

    @Test
    void createDepartment_DuplicateName_ThrowsException() {
        when(departmentRepository.existsByDepartmentName("Engineering")).thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> departmentService.createDepartment(departmentRequest));

        verify(departmentRepository, never()).save(any(Department.class));
    }

    @Test
    void updateDepartment_Success() {
        DepartmentRequest updateRequest = new DepartmentRequest("Engineering Updated", "Updated desc");
        when(departmentRepository.findById(1)).thenReturn(Optional.of(department));
        when(departmentRepository.findByDepartmentName("Engineering Updated")).thenReturn(Optional.empty());
        when(departmentRepository.save(any(Department.class))).thenReturn(department);

        Department result = departmentService.updateDepartment(1, updateRequest);

        assertNotNull(result);
        verify(departmentRepository).save(any(Department.class));
    }

    @Test
    void updateDepartment_NotFound_ThrowsException() {
        when(departmentRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> departmentService.updateDepartment(99, departmentRequest));
    }

    @Test
    void updateDepartment_DuplicateName_DifferentId_ThrowsException() {
        Department existingOther = Department.builder()
                .departmentId(2)
                .departmentName("Engineering")
                .build();
        when(departmentRepository.findById(1)).thenReturn(Optional.of(department));
        when(departmentRepository.findByDepartmentName("Engineering")).thenReturn(Optional.of(existingOther));

        assertThrows(DuplicateResourceException.class,
                () -> departmentService.updateDepartment(1, departmentRequest));
    }

    @Test
    void updateDepartment_SameName_SameId_Success() {
        when(departmentRepository.findById(1)).thenReturn(Optional.of(department));
        when(departmentRepository.findByDepartmentName("Engineering")).thenReturn(Optional.of(department));
        when(departmentRepository.save(any(Department.class))).thenReturn(department);

        Department result = departmentService.updateDepartment(1, departmentRequest);

        assertNotNull(result);
        verify(departmentRepository).save(any(Department.class));
    }

    @Test
    void deactivateDepartment_Success() {
        when(departmentRepository.findById(1)).thenReturn(Optional.of(department));
        when(employeeRepository.countByDepartment_DepartmentId(1)).thenReturn(0L);
        when(departmentRepository.save(any(Department.class))).thenReturn(department);

        Department result = departmentService.deactivateDepartment(1);

        assertNotNull(result);
        assertFalse(result.getIsActive());
        verify(departmentRepository).save(any(Department.class));
    }

    @Test
    void deactivateDepartment_NotFound_ThrowsException() {
        when(departmentRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> departmentService.deactivateDepartment(99));
    }

    @Test
    void deactivateDepartment_HasEmployees_ThrowsException() {
        when(departmentRepository.findById(1)).thenReturn(Optional.of(department));
        when(employeeRepository.countByDepartment_DepartmentId(1)).thenReturn(5L);

        assertThrows(BadRequestException.class,
                () -> departmentService.deactivateDepartment(1));

        verify(departmentRepository, never()).save(any(Department.class));
    }

    @Test
    void deactivateDepartment_AlreadyDeactivated_ThrowsException() {
        department.setIsActive(false);
        when(departmentRepository.findById(1)).thenReturn(Optional.of(department));
        when(employeeRepository.countByDepartment_DepartmentId(1)).thenReturn(0L);

        assertThrows(InvalidActionException.class,
                () -> departmentService.deactivateDepartment(1));
    }

    @Test
    void activateDepartment_Success() {
        department.setIsActive(false);
        when(departmentRepository.findById(1)).thenReturn(Optional.of(department));
        when(departmentRepository.save(any(Department.class))).thenReturn(department);

        Department result = departmentService.activateDepartment(1);

        assertNotNull(result);
        assertTrue(result.getIsActive());
        verify(departmentRepository).save(any(Department.class));
    }

    @Test
    void activateDepartment_NotFound_ThrowsException() {
        when(departmentRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> departmentService.activateDepartment(99));
    }

    @Test
    void activateDepartment_AlreadyActive_ThrowsException() {
        when(departmentRepository.findById(1)).thenReturn(Optional.of(department));

        assertThrows(InvalidActionException.class,
                () -> departmentService.activateDepartment(1));
    }

    @Test
    void getAllDepartments_Success() {
        Department dept2 = Department.builder().departmentId(2).departmentName("HR").build();
        when(departmentRepository.findAll()).thenReturn(Arrays.asList(department, dept2));

        List<Department> result = departmentService.getAllDepartments();

        assertNotNull(result);
        assertEquals(2, result.size());
    }

    @Test
    void getAllDepartments_EmptyList() {
        when(departmentRepository.findAll()).thenReturn(List.of());

        List<Department> result = departmentService.getAllDepartments();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getDepartmentById_Success() {
        when(departmentRepository.findById(1)).thenReturn(Optional.of(department));

        Department result = departmentService.getDepartmentById(1);

        assertNotNull(result);
        assertEquals(1, result.getDepartmentId());
        assertEquals("Engineering", result.getDepartmentName());
    }

    @Test
    void getDepartmentById_NotFound_ThrowsException() {
        when(departmentRepository.findById(99)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> departmentService.getDepartmentById(99));
    }
}
