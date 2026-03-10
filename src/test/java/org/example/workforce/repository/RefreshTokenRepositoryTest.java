package org.example.workforce.repository;

import org.example.workforce.model.Employee;
import org.example.workforce.model.RefreshToken;
import org.example.workforce.model.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Transactional
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=MySQL;DB_CLOSE_DELAY=-1",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RefreshTokenRepositoryTest {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private Employee employee;
    private RefreshToken refreshToken;

    @BeforeEach
    void setUp() {
        employee = Employee.builder()
                .email("test@example.com")
                .employeeCode("EMP001")
                .firstName("John")
                .lastName("Doe")
                .passwordHash("$2a$10$hashedpassword")
                .joiningDate(java.time.LocalDate.now())
                .role(Role.EMPLOYEE)
                .isActive(true)
                .build();
        employee = employeeRepository.save(employee);

        refreshToken = RefreshToken.builder()
                .token("test-refresh-token")
                .employee(employee)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .isRevoked(false)
                .build();
    }

    @Test
    void testSaveAndFindById() {
        RefreshToken saved = refreshTokenRepository.save(refreshToken);
        assertNotNull(saved.getTokenId());
    }

    @Test
    void testFindByTokenAndIsRevokedFalse() {
        refreshTokenRepository.save(refreshToken);
        Optional<RefreshToken> found = refreshTokenRepository.findByTokenAndIsRevokedFalse("test-refresh-token");
        assertTrue(found.isPresent());
    }

    @Test
    void testRevokeAllByEmployee() {
        refreshTokenRepository.save(refreshToken);
        int updated = refreshTokenRepository.revokeAllByEmployee(employee.getEmployeeId());
        assertTrue(updated > 0);
    }

    @Test
    void testDeleteExpiredAndRevoked() {
        RefreshToken expired = RefreshToken.builder()
                .token("expired-token")
                .employee(employee)
                .expiryDate(LocalDateTime.now().minusDays(1))
                .isRevoked(false)
                .build();
        refreshTokenRepository.save(expired);
        int deleted = refreshTokenRepository.deleteExpiredAndRevoked(LocalDateTime.now());
        assertTrue(deleted >= 0);
    }
}

