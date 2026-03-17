package org.example.workforce.controller;

import org.example.workforce.dto.ExpenseActionRequest;
import org.example.workforce.model.Expense;
import org.example.workforce.model.enums.ExpenseStatus;
import org.example.workforce.service.ExpenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Manager Expense endpoints.
 * - Regular managers: view team expenses and approve/reject them.
 * - Finance managers: view ALL expenses, approve at finance level, and mark as reimbursed.
 */
@RestController
@RequestMapping("/api/manager/expenses")
public class ManagerExpenseController {

    @Autowired private ExpenseService expenseService;

    // ─── Team Expenses (for regular manager approval) ───

    // Get team expenses pending approval
    @GetMapping
    public ResponseEntity<Page<Expense>> getTeamExpenses(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(expenseService.getTeamExpenses(auth.getName(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "submittedDate"))));
    }

    // Approve or reject team expense
    @PatchMapping("/{id}/action")
    public ResponseEntity<Expense> actionExpense(
            Authentication auth,
            @PathVariable Integer id,
            @RequestBody ExpenseActionRequest request) {
        return ResponseEntity.ok(expenseService.managerAction(auth.getName(), id, request));
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<Resource> getExpenseReceipt(Authentication auth, @PathVariable Integer id) {
        ExpenseService.ReceiptFileData receipt = expenseService.getExpenseReceipt(auth.getName(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + receipt.fileName() + "\"")
                .contentType(MediaType.parseMediaType(receipt.contentType()))
                .body(receipt.resource());
    }

    // ─── All Expenses (for finance managers — see all employee expenses) ───

    // Get all expenses across the org (optionally filter by status)
    @GetMapping("/all")
    public ResponseEntity<Page<Expense>> getAllExpenses(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        ExpenseStatus expenseStatus = null;
        if (status != null) {
            try { expenseStatus = ExpenseStatus.valueOf(status.toUpperCase()); } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(expenseService.getAllExpenses(expenseStatus,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    // Get expenses pending finance approval
    @GetMapping("/finance-pending")
    public ResponseEntity<Page<Expense>> getFinancePending(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(expenseService.getFinancePendingExpenses(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "managerActionDate"))));
    }

    // Finance action (approve, reject, reimburse) — for finance managers
    @PatchMapping("/{id}/finance-action")
    public ResponseEntity<Expense> financeAction(
            Authentication auth,
            @PathVariable Integer id,
            @RequestBody ExpenseActionRequest request) {
        return ResponseEntity.ok(expenseService.financeAction(auth.getName(), id, request));
    }
}

