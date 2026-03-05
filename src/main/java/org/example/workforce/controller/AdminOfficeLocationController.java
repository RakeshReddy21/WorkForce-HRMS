package org.example.workforce.controller;

import jakarta.validation.Valid;
import org.example.workforce.dto.ApiResponse;
import org.example.workforce.dto.OfficeLocationRequest;
import org.example.workforce.dto.OfficeLocationResponse;
import org.example.workforce.model.OfficeLocation;
import org.example.workforce.service.OfficeLocationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/office-locations")
public class AdminOfficeLocationController {

    @Autowired
    private OfficeLocationService officeLocationService;

    @GetMapping
    public ResponseEntity<ApiResponse> getAllLocations() {
        List<OfficeLocation> locations = officeLocationService.getAllLocations();
        List<OfficeLocationResponse> response = locations.stream()
                .map(this::mapToResponse)
                .toList();
        return ResponseEntity.ok(new ApiResponse(true, "Office locations fetched", response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getLocation(@PathVariable Integer id) {
        OfficeLocation location = officeLocationService.getLocationById(id);
        return ResponseEntity.ok(new ApiResponse(true, "Office location fetched", mapToResponse(location)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse> addLocation(@Valid @RequestBody OfficeLocationRequest request) {
        OfficeLocation created = officeLocationService.addLocation(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse(true, "Office location created", mapToResponse(created)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> updateLocation(@PathVariable Integer id,
                                                       @Valid @RequestBody OfficeLocationRequest request) {
        OfficeLocation updated = officeLocationService.updateLocation(id, request);
        return ResponseEntity.ok(new ApiResponse(true, "Office location updated", mapToResponse(updated)));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse> toggleLocation(@PathVariable Integer id) {
        OfficeLocation toggled = officeLocationService.toggleLocation(id);
        return ResponseEntity.ok(new ApiResponse(true,
                "Office location " + (toggled.getIsActive() ? "activated" : "deactivated"),
                mapToResponse(toggled)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteLocation(@PathVariable Integer id) {
        officeLocationService.deleteLocation(id);
        return ResponseEntity.ok(new ApiResponse(true, "Office location deleted"));
    }

    @GetMapping("/active")
    public ResponseEntity<ApiResponse> getActiveLocations() {
        List<OfficeLocation> locations = officeLocationService.getActiveLocations();
        List<OfficeLocationResponse> response = locations.stream()
                .map(this::mapToResponse)
                .toList();
        return ResponseEntity.ok(new ApiResponse(true, "Active office locations fetched", response));
    }

    private OfficeLocationResponse mapToResponse(OfficeLocation entity) {
        return OfficeLocationResponse.builder()
                .locationId(entity.getLocationId())
                .locationName(entity.getLocationName())
                .address(entity.getAddress())
                .latitude(entity.getLatitude())
                .longitude(entity.getLongitude())
                .radiusMeters(entity.getRadiusMeters())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
