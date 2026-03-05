package org.example.workforce.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.LoginRequest;
import org.example.workforce.model.Employee;
import org.example.workforce.model.RefreshToken;
import org.example.workforce.model.enums.Role;
import org.example.workforce.repository.ActivityLogRepository;
import org.example.workforce.repository.EmployeeRepository;
import org.example.workforce.service.IpAccessControlService;
import org.example.workforce.service.OtpService;
import org.example.workforce.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private EmployeeRepository employeeRepository;

    @MockitoBean
    private JwtUtil jwtUtil;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private ActivityLogRepository activityLogRepository;

    @MockitoBean
    private IpAccessControlService ipAccessControlService;

    @MockitoBean
    private OtpService otpService;

    private ObjectMapper objectMapper = new ObjectMapper();

    private LoginRequest loginRequest;
    private Employee employee;

    @BeforeEach
    void setUp() {
        loginRequest = new LoginRequest();
        loginRequest.setEmail("employee@test.com");
        loginRequest.setPassword("password123");

        employee = Employee.builder()
                .employeeId(1)
                .email("employee@test.com")
                .firstName("John")
                .lastName("Doe")
                .employeeCode("EMP001")
                .role(Role.EMPLOYEE)
                .isActive(true)
                .build();
    }

    @Test
    @WithMockUser
    void testLogin_Success() throws Exception {
        UserDetails userDetails = User.withUsername("employee@test.com")
                .password("password123")
                .roles("EMPLOYEE")
                .build();

        Authentication authentication = mock(Authentication.class);
        when(authentication.getPrincipal()).thenReturn(userDetails);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(employeeRepository.findByEmail("employee@test.com")).thenReturn(Optional.of(employee));
        when(ipAccessControlService.isIpAllowed(anyString())).thenReturn(true);
        when(jwtUtil.generateToken(anyMap(), any(UserDetails.class))).thenReturn("test-access-token");

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenId(1)
                .token("test-refresh-token")
                .employee(employee)
                .expiryDate(LocalDateTime.now().plusDays(7))
                .build();
        when(refreshTokenService.createRefreshToken("employee@test.com")).thenReturn(refreshToken);
        when(activityLogRepository.save(any())).thenReturn(null);

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Login Successful"))
                .andExpect(jsonPath("$.data.accessToken").value("test-access-token"))
                .andExpect(jsonPath("$.data.refreshToken").value("test-refresh-token"))
                .andExpect(jsonPath("$.data.email").value("employee@test.com"));
    }

    @Test
    @WithMockUser
    void testLogin_InvalidCredentials() throws Exception {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
