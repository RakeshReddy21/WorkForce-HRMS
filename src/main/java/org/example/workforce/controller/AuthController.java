package org.example.workforce.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.ApiResponse;
import org.example.workforce.dto.LoginRequest;
import org.example.workforce.dto.RefreshTokenRequest;
import org.example.workforce.exception.ResourceNotFoundException;
import org.example.workforce.model.ActivityLog;
import org.example.workforce.model.Employee;
import org.example.workforce.model.RefreshToken;
import org.example.workforce.repository.ActivityLogRepository;
import org.example.workforce.repository.EmployeeRepository;
import org.example.workforce.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    @Autowired
    private AuthenticationManager authenticationManager;
    @Autowired
    private EmployeeRepository employeeRepository;
    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private RefreshTokenService refreshTokenService;
    @Autowired
    private UserDetailsService userDetailsService;
    @Autowired
    private ActivityLogRepository activityLogRepository;

    // ==================== Login ====================
    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        String ipAddress = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        Employee employee = employeeRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found with email: " + request.getEmail()));

        // Generate access token
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", employee.getRole().name());
        extraClaims.put("employeeId", employee.getEmployeeId());
        extraClaims.put("name", employee.getFirstName() + " " + employee.getLastName());
        String accessToken = jwtUtil.generateToken(extraClaims, userDetails);

        // Generate refresh token
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(employee.getEmail());

        // Log successful login
        activityLogRepository.save(ActivityLog.builder()
                .performedBy(employee)
                .action("LOGIN_SUCCESS")
                .entityType("AUTH")
                .entityId(employee.getEmployeeId())
                .details("Successful login from IP: " + ipAddress)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .status("SUCCESS")
                .build());

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("accessToken", accessToken);
        responseData.put("refreshToken", refreshToken.getToken());
        responseData.put("tokenType", "Bearer");
        responseData.put("employeeId", employee.getEmployeeId());
        responseData.put("employeeCode", employee.getEmployeeCode());
        responseData.put("name", employee.getFirstName() + " " + employee.getLastName());
        responseData.put("email", employee.getEmail());
        responseData.put("role", employee.getRole().name());

        return ResponseEntity.ok(new ApiResponse(true, "Login Successful", responseData));
    }

    // ==================== Refresh Token ====================
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshToken refreshToken = refreshTokenService.verifyRefreshToken(request.getRefreshToken());
        Employee employee = refreshToken.getEmployee();

        UserDetails userDetails = userDetailsService.loadUserByUsername(employee.getEmail());

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", employee.getRole().name());
        extraClaims.put("employeeId", employee.getEmployeeId());
        extraClaims.put("name", employee.getFirstName() + " " + employee.getLastName());
        String newAccessToken = jwtUtil.generateToken(extraClaims, userDetails);

        Map<String, Object> responseData = new HashMap<>();
        responseData.put("accessToken", newAccessToken);
        responseData.put("refreshToken", refreshToken.getToken());
        responseData.put("tokenType", "Bearer");

        return ResponseEntity.ok(new ApiResponse(true, "Token refreshed successfully", responseData));
    }

    // ==================== Logout ====================
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(HttpServletRequest httpRequest) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String email = auth.getName();
            refreshTokenService.revokeTokenByEmployee(email);

            // Log logout
            Employee employee = employeeRepository.findByEmail(email).orElse(null);
            if (employee != null) {
                activityLogRepository.save(ActivityLog.builder()
                        .performedBy(employee)
                        .action("LOGOUT")
                        .entityType("AUTH")
                        .entityId(employee.getEmployeeId())
                        .details("Employee logged out")
                        .ipAddress(getClientIp(httpRequest))
                        .status("SUCCESS")
                        .build());
            }
        }
        return ResponseEntity.ok(new ApiResponse(true, "Logged out successfully. All refresh tokens revoked."));
    }

    // ==================== Helpers ====================
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        return request.getRemoteAddr();
    }
}
