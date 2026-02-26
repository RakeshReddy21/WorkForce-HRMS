package org.example.workforce.controller;
import jakarta.validation.Valid;
import org.example.workforce.dto.ApiResponse;
import org.example.workforce.dto.DepartmentRequest;
import org.example.workforce.model.Department;
import org.example.workforce.service.DepartmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/departments")
public class AdminDepartmentController {
    @Autowired
    private DepartmentService departmentService;

    @PostMapping
    public ResponseEntity<ApiResponse> createDepartment(@Valid @RequestBody DepartmentRequest request) {
        Department department = departmentService.createDepartment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse(true, "Department created successfully", department));
    }

    @PutMapping("/{departmentId}")
    public ResponseEntity<ApiResponse> updateDepartment(@PathVariable Integer departmentId, @Valid @RequestBody DepartmentRequest request) {
        Department department = departmentService.updateDepartment(departmentId, request);
        return ResponseEntity.ok(new ApiResponse(true, "Department updated successfully", department));
    }

    @PatchMapping("/{departmentId}/deactivate")
    public ResponseEntity<ApiResponse> deactivateDepartment(@PathVariable Integer departmentId) {
        Department department = departmentService.deactivateDepartment(departmentId);
        return ResponseEntity.ok(new ApiResponse(true, "Department deactivated successfully", department));
    }

    @PatchMapping("/{departmentId}/activate")
    public ResponseEntity<ApiResponse> activateDepartment(@PathVariable Integer departmentId) {
        Department department = departmentService.activateDepartment(departmentId);
        return ResponseEntity.ok(new ApiResponse(true, "Department activated successfully", department));
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getAllDepartments() {
        List<Department> departments = departmentService.getAllDepartments();
        return ResponseEntity.ok(new ApiResponse(true, "Departments fetched successfully", departments));
    }

    @GetMapping("/{departmentId}")
    public ResponseEntity<ApiResponse> getDepartment(@PathVariable Integer departmentId) {
        Department department = departmentService.getDepartmentById(departmentId);
        return ResponseEntity.ok(new ApiResponse(true, "Department fetched successfully", department));
    }
}
