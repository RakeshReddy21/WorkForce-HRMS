package org.example.workforce.service;

import org.example.workforce.exception.InvalidActionException;
import org.example.workforce.exception.ResourceNotFoundException;
import org.example.workforce.model.Employee;
import org.example.workforce.model.RefreshToken;
import org.example.workforce.model.enums.Role;
import org.example.workforce.repository.EmployeeRepository;
import org.example.workforce.repository.RefreshTokenRepository;
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
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private Employee employee;
    private RefreshToken refreshToken;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenExpiration", 604800000L);

        employee = Employee.builder()
                .employeeId(1)
                .email("emp@test.com")
                .firstName("John")
                .lastName("Doe")
                .role(Role.EMPLOYEE)
                .isActive(true)
                .build();

        refreshToken = RefreshToken.builder()
                .tokenId(1)
                .employee(employee)
                .token("test-refresh-token-uuid")
                .expiryDate(LocalDateTime.now().plusDays(7))
                .isRevoked(false)
                .build();
    }

    @Test
    void createRefreshToken_EmployeeNotFound_ThrowsException() {
        when(employeeRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> refreshTokenService.createRefreshToken("unknown@test.com"));
    }

    @Test
    void verifyRefreshToken_InvalidToken_ThrowsException() {
        when(refreshTokenRepository.findByTokenAndIsRevokedFalse("invalid-token"))
                .thenReturn(Optional.empty());

        assertThrows(InvalidActionException.class,
                () -> refreshTokenService.verifyRefreshToken("invalid-token"));
    }

    @Test
    void verifyRefreshToken_ExpiredToken_ThrowsException() {
        RefreshToken expired = RefreshToken.builder()
                .tokenId(2)
                .employee(employee)
                .token("expired-token")
                .expiryDate(LocalDateTime.now().minusDays(1))
                .isRevoked(false)
                .build();

        when(refreshTokenRepository.findByTokenAndIsRevokedFalse("expired-token"))
                .thenReturn(Optional.of(expired));

        assertThrows(InvalidActionException.class,
                () -> refreshTokenService.verifyRefreshToken("expired-token"));

        verify(refreshTokenRepository).delete(expired);
    }


    @Test
    void revokeTokenByEmployee_EmployeeNotFound_ThrowsException() {
        when(employeeRepository.findByEmail("unknown@test.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> refreshTokenService.revokeTokenByEmployee("unknown@test.com"));
    }

    @Test
    void revokeToken_Success() {
        when(refreshTokenRepository.findByTokenAndIsRevokedFalse("test-refresh-token-uuid"))
                .thenReturn(Optional.of(refreshToken));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(refreshToken);

        refreshTokenService.revokeToken("test-refresh-token-uuid");

        assertTrue(refreshToken.getIsRevoked());
        verify(refreshTokenRepository).save(refreshToken);
    }

    @Test
    void revokeToken_AlreadyRevoked_ThrowsException() {
        when(refreshTokenRepository.findByTokenAndIsRevokedFalse("revoked-token"))
                .thenReturn(Optional.empty());

        assertThrows(InvalidActionException.class,
                () -> refreshTokenService.revokeToken("revoked-token"));
    }

    @Test
    void cleanupExpiredTokens_Success() {
        when(refreshTokenRepository.deleteExpiredAndRevoked(any(LocalDateTime.class))).thenReturn(3);

        int result = refreshTokenService.cleanupExpiredTokens();

        assertEquals(3, result);
        verify(refreshTokenRepository).deleteExpiredAndRevoked(any(LocalDateTime.class));
    }

    @Test
    void cleanupExpiredTokens_NoneToClean_ReturnsZero() {
        when(refreshTokenRepository.deleteExpiredAndRevoked(any(LocalDateTime.class))).thenReturn(0);

        int result = refreshTokenService.cleanupExpiredTokens();

        assertEquals(0, result);
    }
}
