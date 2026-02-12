package org.example.workforce.controller;
import jakarta.validation.Valid;
import org.example.workforce.config.JwtUtil;
import org.example.workforce.dto.ApiResponse;
import org.example.workforce.dto.LoginRequest;
import org.example.workforce.model.Employee;
import org.example.workforce.repository.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
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
    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(@Valid @RequestBody LoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            Employee employee = employeeRepository.findByEmail(request.getEmail()).orElseThrow(() -> new RuntimeException("Employee not found"));
            Map<String, Object> extraClaims = new HashMap<>();
            extraClaims.put("role", employee.getRole().name());
            extraClaims.put("employeeId", employee.getEmployeeId());
            extraClaims.put("name", employee.getFirstName() + " " + employee.getLastName());
            String token = jwtUtil.generateToken(extraClaims, userDetails);
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("token", token);
            responseData.put("employeeId", employee.getEmployeeId());
            responseData.put("employeeCode", employee.getEmployeeCode());
            responseData.put("name", employee.getFirstName() + " " + employee.getLastName());
            responseData.put("email", employee.getEmail());
            responseData.put("role", employee.getRole().name());
            return ResponseEntity.ok(new ApiResponse(true, "Login Successful", responseData));
        } catch (AuthenticationException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ApiResponse(false, "Invalid email or password"));
        }
    }
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout() {
        return ResponseEntity.ok(new ApiResponse(true, "Logged out successfully. Please discard your token."));
    }
}