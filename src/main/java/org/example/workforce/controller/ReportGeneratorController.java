package org.example.workforce.controller;

import org.example.workforce.dto.PerformanceReportResponse;
import org.example.workforce.service.ReportGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * AI Performance Report Generator endpoint.
 * Available to managers and admins.
 */
@RestController
@RequestMapping("/api/admin/reports")
public class ReportGeneratorController {

    @Autowired private ReportGeneratorService reportGeneratorService;

    @GetMapping("/performance/{employeeId}")
    public ResponseEntity<PerformanceReportResponse> generateReport(
            @PathVariable Integer employeeId,
            @RequestParam(required = false) String period) {
        return ResponseEntity.ok(reportGeneratorService.generateReport(employeeId, period));
    }
}

