package org.example.workforce.service;

import org.example.workforce.model.Employee;
import org.example.workforce.model.enums.Role;
import org.example.workforce.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private Employee activeEmployee;
    private Employee inactiveEmployee;

    @BeforeEach
    void setUp() {
        activeEmployee = Employee.builder()
                .employeeId(1)
                .email("active@test.com")
                .passwordHash("$2a$10$hashedPassword")
                .firstName("John")
                .lastName("Doe")
                .role(Role.EMPLOYEE)
                .isActive(true)
                .build();

        inactiveEmployee = Employee.builder()
                .employeeId(2)
                .email("inactive@test.com")
                .passwordHash("$2a$10$hashedPassword")
                .firstName("Jane")
                .lastName("Smith")
                .role(Role.MANAGER)
                .isActive(false)
                .build();
    }

    @Test
    void loadUserByUsername_ActiveEmployee_Success() {
        when(employeeRepository.findByEmail("active@test.com")).thenReturn(Optional.of(activeEmployee));

        UserDetails result = customUserDetailsService.loadUserByUsername("active@test.com");

        assertNotNull(result);
        assertEquals("active@test.com", result.getUsername());
        assertEquals("$2a$10$hashedPassword", result.getPassword());
        assertTrue(result.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_EMPLOYEE")));
    }

    @Test
    void loadUserByUsername_ManagerRole_HasCorrectAuthority() {
        Employee manager = Employee.builder()
                .employeeId(3)
                .email("manager@test.com")
                .passwordHash("$2a$10$hashedPassword")
                .role(Role.MANAGER)
                .isActive(true)
                .build();

        when(employeeRepository.findByEmail("manager@test.com")).thenReturn(Optional.of(manager));

        UserDetails result = customUserDetailsService.loadUserByUsername("manager@test.com");

        assertTrue(result.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_MANAGER")));
    }

    @Test
    void loadUserByUsername_AdminRole_HasCorrectAuthority() {
        Employee admin = Employee.builder()
                .employeeId(4)
                .email("admin@test.com")
                .passwordHash("$2a$10$hashedPassword")
                .role(Role.ADMIN)
                .isActive(true)
                .build();

        when(employeeRepository.findByEmail("admin@test.com")).thenReturn(Optional.of(admin));

        UserDetails result = customUserDetailsService.loadUserByUsername("admin@test.com");

        assertTrue(result.getAuthorities().stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void loadUserByUsername_InactiveEmployee_ThrowsDisabledException() {
        when(employeeRepository.findByEmail("inactive@test.com")).thenReturn(Optional.of(inactiveEmployee));

        DisabledException exception = assertThrows(DisabledException.class,
                () -> customUserDetailsService.loadUserByUsername("inactive@test.com"));

        assertTrue(exception.getMessage().contains("deactivated"));
    }

    @Test
    void loadUserByUsername_EmployeeNotFound_ThrowsUsernameNotFoundException() {
        when(employeeRepository.findByEmail("nonexistent@test.com")).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class,
                () -> customUserDetailsService.loadUserByUsername("nonexistent@test.com"));
    }

    @Test
    void loadUserByUsername_HasExactlyOneAuthority() {
        when(employeeRepository.findByEmail("active@test.com")).thenReturn(Optional.of(activeEmployee));

        UserDetails result = customUserDetailsService.loadUserByUsername("active@test.com");

        assertEquals(1, result.getAuthorities().size());
    }
}
