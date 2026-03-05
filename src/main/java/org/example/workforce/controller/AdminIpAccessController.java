package org.example.workforce.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.example.workforce.dto.ApiResponse;
import org.example.workforce.dto.IpRangeRequest;
import org.example.workforce.dto.IpRangeResponse;
import org.example.workforce.model.AllowedIpRange;
import org.example.workforce.service.IpAccessControlService;
import org.example.workforce.util.NetworkIpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/ip-access")
public class AdminIpAccessController {

    @Autowired
    private IpAccessControlService ipAccessControlService;

    @GetMapping
    public ResponseEntity<ApiResponse> getAllIpRanges() {
        List<AllowedIpRange> ranges = ipAccessControlService.getAllIpRanges();
        List<IpRangeResponse> responseList = ranges.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(new ApiResponse(true, "IP ranges fetched successfully", responseList));
    }

    @GetMapping("/my-ip")
    public ResponseEntity<ApiResponse> getMyIp(HttpServletRequest request) {
        String clientIp = NetworkIpUtil.resolveClientIp(request);
        return ResponseEntity.ok(new ApiResponse(true, "Your current IP address", clientIp));
    }

    @PostMapping
    public ResponseEntity<ApiResponse> addIpRange(@Valid @RequestBody IpRangeRequest request) {
        AllowedIpRange saved = ipAccessControlService.addIpRange(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse(true, "IP range added successfully", mapToResponse(saved)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse> updateIpRange(
            @PathVariable Integer id,
            @Valid @RequestBody IpRangeRequest request) {
        AllowedIpRange updated = ipAccessControlService.updateIpRange(id, request);
        return ResponseEntity.ok(new ApiResponse(true, "IP range updated successfully", mapToResponse(updated)));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<ApiResponse> toggleIpRange(@PathVariable Integer id) {
        AllowedIpRange toggled = ipAccessControlService.toggleIpRange(id);
        String status = toggled.getIsActive() ? "activated" : "deactivated";
        return ResponseEntity.ok(new ApiResponse(true, "IP range " + status + " successfully", mapToResponse(toggled)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteIpRange(@PathVariable Integer id) {
        ipAccessControlService.deleteIpRange(id);
        return ResponseEntity.ok(new ApiResponse(true, "IP range deleted successfully"));
    }

    @GetMapping("/check")
    public ResponseEntity<ApiResponse> checkIp(@RequestParam String ip) {
        boolean allowed = ipAccessControlService.isIpAllowed(ip);
        String msg = allowed ? "IP " + ip + " is ALLOWED" : "IP " + ip + " is BLOCKED";
        return ResponseEntity.ok(new ApiResponse(true, msg, allowed));
    }

    private IpRangeResponse mapToResponse(AllowedIpRange entity) {
        return IpRangeResponse.builder()
                .ipRangeId(entity.getIpRangeId())
                .ipRange(entity.getIpRange())
                .description(entity.getDescription())
                .isActive(entity.getIsActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
