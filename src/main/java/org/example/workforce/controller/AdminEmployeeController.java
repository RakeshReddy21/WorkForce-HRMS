package org.example.workforce.controller;
import jakarta.validation.Valid;
import org.example.workforce.dto.*;
import org.example.workforce.model.Employee;
import org.example.workforce.service.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/employees")
public class AdminEmployeeController {
    @Autowired
    private EmployeeService employeeService;
    @PostMapping("/register")
    public ResponseEntity<ApiResponse> registerEmployee(@Valid @RequestBody RegisterEmployeeRequest request){
        try{
            Employee employee = employeeService.registerEmployee(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse(true, "Employee registered successfully", employee));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }
    @GetMapping("/{employeeCode}")
    public ResponseEntity<ApiResponse> getEmployee(@PathVariable String employeeCode){
        try {
            EmployeeProfileResponse profile = employeeService.getEmployeeByCode(employeeCode);
            return ResponseEntity.ok(new ApiResponse(true, "Employee fetched successfully", profile));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }
    @GetMapping
    public ResponseEntity<ApiResponse> getAllEmployees(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer departmentId,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "employeeId") String sortBy,
            @RequestParam(defaultValue = "asc") String direction){
        try{
            Sort sort = direction.equalsIgnoreCase("desc") ? Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<EmployeeProfileResponse> employees = employeeService.getEmployees(keyword, departmentId, role, active, pageable);
            return ResponseEntity.ok(new ApiResponse(true, "Employees fetched successfully", employees));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }
    @PutMapping("/{employeeCode}")
    public ResponseEntity<ApiResponse> updateEmployee(@PathVariable String employeeCode, @Valid @RequestBody UpdateEmployeeRequest request){
        try{
            String adminEmail = getAdminEmail();
            EmployeeProfileResponse profile = employeeService.updateEmployeeByAdmin(employeeCode, request, adminEmail);
            return ResponseEntity.ok(new ApiResponse(true, "Employee updated successfully", profile));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }
    @PatchMapping("/{employeeCode}/deactivate")
    public ResponseEntity<ApiResponse> deactivateEmployee(@PathVariable String employeeCode){
        try{
            String adminEmail = getAdminEmail();
            EmployeeProfileResponse profile = employeeService.deactivateEmployee(employeeCode, adminEmail);
            return ResponseEntity.ok(new ApiResponse(true, "Employee deactivated successfully", profile));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }
    @PutMapping("/{employeeCode}/activate")
    public ResponseEntity<ApiResponse> activateEmployee(@PathVariable String employeeCode){
        try {
            String adminEmail = getAdminEmail();
            EmployeeProfileResponse profile = employeeService.activateEmployee(employeeCode, adminEmail);
            return ResponseEntity.ok(new ApiResponse(true, "Employee reactivated successfully", profile));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }
    @PatchMapping("/{employeeCode}/manager")
    public ResponseEntity<ApiResponse> assignManager(@PathVariable String employeeCode, @Valid @RequestBody AssignManagerRequest request){
        try{
            String adminEmail = getAdminEmail();
            EmployeeProfileResponse profile = employeeService.assignManager(employeeCode, request.getManagerCode(), adminEmail);
            return ResponseEntity.ok(new ApiResponse(true, "Manager assigned successfully", profile));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }
    private String getAdminEmail(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if(auth == null || !auth.isAuthenticated()){
            throw new RuntimeException("Admin not authenticated");
        }
        return auth.getName();
    }
}
