package org.example.workforce.controller;
import jakarta.validation.Valid;
import org.example.workforce.dto.ApiResponse;
import org.example.workforce.dto.DesignationRequest;
import org.example.workforce.model.Designation;
import org.example.workforce.service.DesignationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/admin/designations")
public class AdminDesignationController {
    @Autowired
    private DesignationService designationService;

    @PostMapping
    public ResponseEntity<ApiResponse> createDesignation(@Valid @RequestBody DesignationRequest request) {
        Designation designation = designationService.createDesignation(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponse(true, "Designation created successfully", designation));
    }

    @PutMapping("/{designationId}")
    public ResponseEntity<ApiResponse> updateDesignation(@PathVariable Integer designationId, @Valid @RequestBody DesignationRequest request) {
        Designation designation = designationService.updateDesignation(designationId, request);
        return ResponseEntity.ok(new ApiResponse(true, "Designation updated successfully", designation));
    }

    @PatchMapping("/{designationId}/deactivate")
    public ResponseEntity<ApiResponse> deactivateDesignation(@PathVariable Integer designationId) {
        Designation designation = designationService.deactivateDesignation(designationId);
        return ResponseEntity.ok(new ApiResponse(true, "Designation deactivated successfully", designation));
    }

    @PatchMapping("/{designationId}/activate")
    public ResponseEntity<ApiResponse> activateDesignation(@PathVariable Integer designationId) {
        Designation designation = designationService.activateDesignation(designationId);
        return ResponseEntity.ok(new ApiResponse(true, "Designation activated successfully", designation));
    }

    @GetMapping
    public ResponseEntity<ApiResponse> getAllDesignations() {
        List<Designation> designations = designationService.getAllDesignations();
        return ResponseEntity.ok(new ApiResponse(true, "Designations fetched successfully", designations));
    }

    @GetMapping("/{designationId}")
    public ResponseEntity<ApiResponse> getDesignation(@PathVariable Integer designationId) {
        Designation designation = designationService.getDesignationById(designationId);
        return ResponseEntity.ok(new ApiResponse(true, "Designation fetched successfully", designation));
    }
}
