package org.example.workforce.controller;

import org.example.workforce.dto.ExpenseActionRequest;
import org.example.workforce.model.Expense;
import org.example.workforce.service.ExpenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Manager Expense endpoints.
 * Managers can view team expenses and approve/reject them.
 */
@RestController
@RequestMapping("/api/manager/expenses")
public class ManagerExpenseController {

    @Autowired private ExpenseService expenseService;

    // Get team expenses pending approval
    @GetMapping
    public ResponseEntity<Page<Expense>> getTeamExpenses(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(expenseService.getTeamExpenses(auth.getName(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedDate"))));
    }

    // Approve or reject
    @PatchMapping("/{id}/action")
    public ResponseEntity<Expense> actionExpense(
            Authentication auth,
            @PathVariable Integer id,
            @RequestBody ExpenseActionRequest request) {
        return ResponseEntity.ok(expenseService.managerAction(auth.getName(), id, request));
    }
}

