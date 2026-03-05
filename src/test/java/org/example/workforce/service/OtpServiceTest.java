package org.example.workforce.service;

import org.example.workforce.exception.BadRequestException;
import org.example.workforce.model.Employee;
import org.example.workforce.model.OtpVerification;
import org.example.workforce.model.enums.Role;
import org.example.workforce.repository.OtpVerificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    private OtpVerificationRepository otpRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private OtpService otpService;

    private Employee employee;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(otpService, "otpExpiryMinutes", 5);
        ReflectionTestUtils.setField(otpService, "maxAttempts", 5);

        employee = Employee.builder()
                .employeeId(1)
                .email("emp@test.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.EMPLOYEE)
                .isActive(true)
                .build();
    }

    @Test
    void generateAndSendOtp_Success() {
        doNothing().when(otpRepository).invalidateAllOtpsForEmployee(1);
        when(otpRepository.save(any(OtpVerification.class))).thenAnswer(i -> i.getArgument(0));
        doNothing().when(emailService).sendOtpEmail(anyString(), anyString(), anyString());

        String preAuthToken = otpService.generateAndSendOtp(employee);

        assertNotNull(preAuthToken);
        assertFalse(preAuthToken.isBlank());
        verify(otpRepository).invalidateAllOtpsForEmployee(1);
        verify(otpRepository).save(any(OtpVerification.class));
        verify(emailService).sendOtpEmail(eq("emp@test.com"), eq("John Doe"), anyString());
    }

    @Test
    void generateAndSendOtp_InvalidatesExistingOtps() {
        doNothing().when(otpRepository).invalidateAllOtpsForEmployee(1);
        when(otpRepository.save(any(OtpVerification.class))).thenAnswer(i -> i.getArgument(0));
        doNothing().when(emailService).sendOtpEmail(anyString(), anyString(), anyString());

        otpService.generateAndSendOtp(employee);

        verify(otpRepository, times(1)).invalidateAllOtpsForEmployee(1);
    }

    @Test
    void verifyOtp_ValidOtp_Success() {
        OtpVerification otpRecord = OtpVerification.builder()
                .otpId(1)
                .employee(employee)
                .otp("123456")
                .preAuthToken("test-token")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .attempts(0)
                .isUsed(false)
                .build();

        when(otpRepository.findByPreAuthTokenAndIsUsedFalse("test-token"))
                .thenReturn(Optional.of(otpRecord));
        when(otpRepository.save(any(OtpVerification.class))).thenReturn(otpRecord);

        Employee result = otpService.verifyOtp("test-token", "123456");

        assertNotNull(result);
        assertEquals("emp@test.com", result.getEmail());
        assertTrue(otpRecord.getIsUsed());
    }

    @Test
    void verifyOtp_InvalidPreAuthToken_ThrowsException() {
        when(otpRepository.findByPreAuthTokenAndIsUsedFalse("invalid-token"))
                .thenReturn(Optional.empty());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> otpService.verifyOtp("invalid-token", "123456"));

        assertTrue(ex.getMessage().contains("Invalid or expired"));
    }

    @Test
    void verifyOtp_ExpiredOtp_ThrowsException() {
        OtpVerification expiredRecord = OtpVerification.builder()
                .otpId(1)
                .employee(employee)
                .otp("123456")
                .preAuthToken("test-token")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .attempts(0)
                .isUsed(false)
                .build();

        when(otpRepository.findByPreAuthTokenAndIsUsedFalse("test-token"))
                .thenReturn(Optional.of(expiredRecord));
        when(otpRepository.save(any(OtpVerification.class))).thenReturn(expiredRecord);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> otpService.verifyOtp("test-token", "123456"));

        assertTrue(ex.getMessage().contains("expired"));
        assertTrue(expiredRecord.getIsUsed());
    }

    @Test
    void verifyOtp_MaxAttemptsReached_ThrowsException() {
        OtpVerification otpRecord = OtpVerification.builder()
                .otpId(1)
                .employee(employee)
                .otp("123456")
                .preAuthToken("test-token")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .attempts(5)
                .isUsed(false)
                .build();

        when(otpRepository.findByPreAuthTokenAndIsUsedFalse("test-token"))
                .thenReturn(Optional.of(otpRecord));
        when(otpRepository.save(any(OtpVerification.class))).thenReturn(otpRecord);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> otpService.verifyOtp("test-token", "123456"));

        assertTrue(ex.getMessage().contains("Too many incorrect attempts"));
        assertTrue(otpRecord.getIsUsed());
    }

    @Test
    void verifyOtp_WrongOtp_ThrowsException() {
        OtpVerification otpRecord = OtpVerification.builder()
                .otpId(1)
                .employee(employee)
                .otp("123456")
                .preAuthToken("test-token")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .attempts(0)
                .isUsed(false)
                .build();

        when(otpRepository.findByPreAuthTokenAndIsUsedFalse("test-token"))
                .thenReturn(Optional.of(otpRecord));
        when(otpRepository.save(any(OtpVerification.class))).thenReturn(otpRecord);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> otpService.verifyOtp("test-token", "999999"));

        assertTrue(ex.getMessage().contains("Invalid OTP"));
        assertEquals(1, otpRecord.getAttempts());
    }

    @Test
    void verifyOtp_WrongOtp_ShowsRemainingAttempts() {
        OtpVerification otpRecord = OtpVerification.builder()
                .otpId(1)
                .employee(employee)
                .otp("123456")
                .preAuthToken("test-token")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .attempts(3)
                .isUsed(false)
                .build();

        when(otpRepository.findByPreAuthTokenAndIsUsedFalse("test-token"))
                .thenReturn(Optional.of(otpRecord));
        when(otpRepository.save(any(OtpVerification.class))).thenReturn(otpRecord);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> otpService.verifyOtp("test-token", "999999"));

        assertTrue(ex.getMessage().contains("1 attempt(s) remaining"));
    }

    @Test
    void verifyOtp_WhitespaceOtp_TrimmedAndMatches() {
        OtpVerification otpRecord = OtpVerification.builder()
                .otpId(1)
                .employee(employee)
                .otp("123456")
                .preAuthToken("test-token")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .attempts(0)
                .isUsed(false)
                .build();

        when(otpRepository.findByPreAuthTokenAndIsUsedFalse("test-token"))
                .thenReturn(Optional.of(otpRecord));
        when(otpRepository.save(any(OtpVerification.class))).thenReturn(otpRecord);

        Employee result = otpService.verifyOtp("test-token", " 123456 ");

        assertNotNull(result);
    }

    @Test
    void resendOtp_ValidSession_GeneratesNewOtp() {
        OtpVerification existingOtp = OtpVerification.builder()
                .otpId(1)
                .employee(employee)
                .otp("111111")
                .preAuthToken("old-token")
                .expiresAt(LocalDateTime.now().plusMinutes(5))
                .attempts(2)
                .isUsed(false)
                .build();

        when(otpRepository.findByPreAuthTokenAndIsUsedFalse("old-token"))
                .thenReturn(Optional.of(existingOtp));
        doNothing().when(otpRepository).invalidateAllOtpsForEmployee(1);
        when(otpRepository.save(any(OtpVerification.class))).thenAnswer(i -> i.getArgument(0));
        doNothing().when(emailService).sendOtpEmail(anyString(), anyString(), anyString());

        String newToken = otpService.resendOtp("old-token");

        assertNotNull(newToken);
        verify(otpRepository).invalidateAllOtpsForEmployee(1);
    }

    @Test
    void resendOtp_InvalidSession_ThrowsException() {
        when(otpRepository.findByPreAuthTokenAndIsUsedFalse("bad-token"))
                .thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> otpService.resendOtp("bad-token"));
    }

    @Test
    void cleanupExpiredOtps_Success() {
        doNothing().when(otpRepository).deleteExpiredOtps(any(LocalDateTime.class));

        otpService.cleanupExpiredOtps();

        verify(otpRepository).deleteExpiredOtps(any(LocalDateTime.class));
    }
}
