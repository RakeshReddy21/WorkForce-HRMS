package org.example.workforce.repository;

import org.example.workforce.model.Employee;
import org.example.workforce.model.OtpVerification;
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
class OtpVerificationRepositoryTest {

    @Autowired
    private OtpVerificationRepository otpVerificationRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    private Employee employee;
    private OtpVerification otpVerification;

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

        otpVerification = OtpVerification.builder()
                .employee(employee)
                .otp("123456")
                .preAuthToken("pre-auth-token")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .isUsed(false)
                .build();
    }

    @Test
    void testSaveAndFindById() {
        OtpVerification saved = otpVerificationRepository.save(otpVerification);
        assertNotNull(saved.getOtpId());
    }

    @Test
    void testFindByPreAuthTokenAndIsUsedFalse() {
        otpVerificationRepository.save(otpVerification);
        Optional<OtpVerification> found = otpVerificationRepository.findByPreAuthTokenAndIsUsedFalse("pre-auth-token");
        assertTrue(found.isPresent());
    }

    @Test
    void testDeleteExpiredOtps() {
        OtpVerification expired = OtpVerification.builder()
                .employee(employee)
                .otp("654321")
                .preAuthToken("expired-token")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .isUsed(false)
                .build();
        otpVerificationRepository.save(expired);
        otpVerificationRepository.deleteExpiredOtps(LocalDateTime.now());
        assertFalse(otpVerificationRepository.findByPreAuthTokenAndIsUsedFalse("expired-token").isPresent());
    }
}

