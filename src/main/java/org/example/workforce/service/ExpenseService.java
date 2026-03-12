package org.example.workforce.service;

import org.example.workforce.dto.ExpenseActionRequest;
import org.example.workforce.dto.ExpenseRequest;
import org.example.workforce.exception.BadRequestException;
import org.example.workforce.exception.ResourceNotFoundException;
import org.example.workforce.model.Employee;
import org.example.workforce.model.Expense;
import org.example.workforce.model.ExpenseItem;
import org.example.workforce.model.enums.ExpenseCategory;
import org.example.workforce.model.enums.ExpenseStatus;
import org.example.workforce.model.enums.NotificationType;
import org.example.workforce.model.enums.Role;
import org.example.workforce.repository.ExpenseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExpenseService {

    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private EmployeeService employeeService;
    @Autowired private NotificationService notificationService;

    // ─── Employee: Create Expense ───
    @Transactional
    public Expense createExpense(String email, ExpenseRequest request) {
        Employee employee = employeeService.getEmployeeByEmail(email);

        Expense expense = Expense.builder()
                .employee(employee)
                .title(request.getTitle())
                .description(request.getDescription())
                .category(parseCategory(request.getCategory()))
                .totalAmount(request.getTotalAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "INR")
                .expenseDate(request.getExpenseDate())
                .vendorName(request.getVendorName())
                .invoiceNumber(request.getInvoiceNumber())
                .receiptFileName(request.getReceiptFileName())
                .status(ExpenseStatus.DRAFT)
                .build();

        // Add line items if provided
        if (request.getItems() != null) {
            for (ExpenseRequest.ExpenseItemRequest itemReq : request.getItems()) {
                ExpenseItem item = ExpenseItem.builder()
                        .description(itemReq.getDescription())
                        .amount(itemReq.getAmount())
                        .quantity(itemReq.getQuantity() != null ? itemReq.getQuantity() : 1)
                        .build();
                expense.addItem(item);
            }
        }

        return expenseRepository.save(expense);
    }

    // ─── Employee: Submit Expense for approval ───
    @Transactional
    public Expense submitExpense(String email, Integer expenseId) {
        Employee employee = employeeService.getEmployeeByEmail(email);
        Expense expense = getExpenseById(expenseId);

        validateOwnership(expense, employee);
        if (expense.getStatus() != ExpenseStatus.DRAFT) {
            throw new BadRequestException("Only draft expenses can be submitted.");
        }

        expense.setStatus(ExpenseStatus.SUBMITTED);
        expense.setSubmittedDate(LocalDateTime.now());
        Expense saved = expenseRepository.save(expense);

        // Notify manager
        if (employee.getManager() != null) {
            notificationService.sendNotification(
                    employee.getManager(), "Expense Submitted",
                    "New expense claim from " + employee.getFirstName() + " " + employee.getLastName()
                            + " — ₹" + expense.getTotalAmount(),
                    NotificationType.EXPENSE_SUBMITTED, saved.getExpenseId(), "EXPENSE");
        }

        return saved;
    }

    // ─── Employee: My Expenses ───
    public Page<Expense> getMyExpenses(String email, Pageable pageable) {
        Employee employee = employeeService.getEmployeeByEmail(email);
        return expenseRepository.findByEmployeeEmployeeId(employee.getEmployeeId(), pageable);
    }

    // ─── Manager: Team Expenses pending approval ───
    public Page<Expense> getTeamExpenses(String email, Pageable pageable) {
        Employee manager = employeeService.getEmployeeByEmail(email);
        return expenseRepository.findTeamExpensesByStatus(
                manager.getEmployeeId(), ExpenseStatus.SUBMITTED, pageable);
    }

    // ─── Manager: Approve/Reject Expense ───
    @Transactional
    public Expense managerAction(String email, Integer expenseId, ExpenseActionRequest request) {
        Employee manager = employeeService.getEmployeeByEmail(email);
        Expense expense = getExpenseById(expenseId);

        if (expense.getStatus() != ExpenseStatus.SUBMITTED) {
            throw new BadRequestException("This expense is not pending manager approval.");
        }

        // Verify the manager manages this employee
        Employee expenseOwner = expense.getEmployee();
        if (expenseOwner.getManager() == null ||
                !expenseOwner.getManager().getEmployeeId().equals(manager.getEmployeeId())) {
            if (manager.getRole() != Role.ADMIN) {
                throw new BadRequestException("You are not authorized to action this expense.");
            }
        }

        if ("APPROVED".equalsIgnoreCase(request.getAction())) {
            expense.setStatus(ExpenseStatus.MANAGER_APPROVED);
            expense.setManagerComments(request.getComments());
            expense.setActionedBy(manager);
            expense.setManagerActionDate(LocalDateTime.now());

            notificationService.sendNotification(expenseOwner, "Expense Approved",
                    "Your expense '" + expense.getTitle() + "' was approved by manager. Pending finance review.",
                    NotificationType.EXPENSE_APPROVED, expenseId, "EXPENSE");
        } else if ("REJECTED".equalsIgnoreCase(request.getAction())) {
            expense.setStatus(ExpenseStatus.REJECTED);
            expense.setRejectionReason(request.getComments());
            expense.setActionedBy(manager);
            expense.setManagerActionDate(LocalDateTime.now());

            notificationService.sendNotification(expenseOwner, "Expense Rejected",
                    "Your expense '" + expense.getTitle() + "' was rejected by manager: " + request.getComments(),
                    NotificationType.EXPENSE_REJECTED, expenseId, "EXPENSE");
        } else {
            throw new BadRequestException("Invalid action. Use APPROVED or REJECTED.");
        }

        return expenseRepository.save(expense);
    }

    // ─── Finance/Admin: Expenses pending finance approval ───
    public Page<Expense> getFinancePendingExpenses(Pageable pageable) {
        return expenseRepository.findByStatus(ExpenseStatus.MANAGER_APPROVED, pageable);
    }

    // ─── Finance/Admin: All expenses ───
    public Page<Expense> getAllExpenses(ExpenseStatus status, Pageable pageable) {
        if (status != null) {
            return expenseRepository.findByStatus(status, pageable);
        }
        return expenseRepository.findAll(pageable);
    }

    // ─── Finance: Approve/Reject/Reimburse ───
    @Transactional
    public Expense financeAction(String email, Integer expenseId, ExpenseActionRequest request) {
        Employee financeUser = employeeService.getEmployeeByEmail(email);
        Expense expense = getExpenseById(expenseId);

        if (expense.getStatus() != ExpenseStatus.MANAGER_APPROVED
                && expense.getStatus() != ExpenseStatus.FINANCE_APPROVED) {
            throw new BadRequestException("This expense is not pending finance action.");
        }

        Employee expenseOwner = expense.getEmployee();

        switch (request.getAction().toUpperCase()) {
            case "APPROVED" -> {
                expense.setStatus(ExpenseStatus.FINANCE_APPROVED);
                expense.setFinanceComments(request.getComments());
                expense.setFinanceActionedBy(financeUser);
                expense.setFinanceActionDate(LocalDateTime.now());
                notificationService.sendNotification(expenseOwner, "Expense Finance Approved",
                        "Your expense '" + expense.getTitle() + "' has been approved by finance.",
                        NotificationType.EXPENSE_APPROVED, expenseId, "EXPENSE");
            }
            case "REJECTED" -> {
                expense.setStatus(ExpenseStatus.REJECTED);
                expense.setRejectionReason(request.getComments());
                expense.setFinanceActionedBy(financeUser);
                expense.setFinanceActionDate(LocalDateTime.now());
                notificationService.sendNotification(expenseOwner, "Expense Rejected",
                        "Your expense '" + expense.getTitle() + "' was rejected by finance: " + request.getComments(),
                        NotificationType.EXPENSE_REJECTED, expenseId, "EXPENSE");
            }
            case "REIMBURSED" -> {
                expense.setStatus(ExpenseStatus.REIMBURSED);
                expense.setFinanceComments(request.getComments());
                expense.setFinanceActionedBy(financeUser);
                expense.setReimbursedDate(LocalDateTime.now());
                notificationService.sendNotification(expenseOwner, "Expense Reimbursed",
                        "Your expense '" + expense.getTitle() + "' of ₹" + expense.getTotalAmount() + " has been reimbursed!",
                        NotificationType.EXPENSE_REIMBURSED, expenseId, "EXPENSE");
            }
            default -> throw new BadRequestException("Invalid action. Use APPROVED, REJECTED, or REIMBURSED.");
        }

        return expenseRepository.save(expense);
    }

    // ─── Helpers ───
    public Expense getExpenseById(Integer id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + id));
    }

    private void validateOwnership(Expense expense, Employee employee) {
        if (!expense.getEmployee().getEmployeeId().equals(employee.getEmployeeId())) {
            throw new BadRequestException("You can only modify your own expenses.");
        }
    }

    private ExpenseCategory parseCategory(String category) {
        if (category == null) return ExpenseCategory.OTHER;
        try {
            return ExpenseCategory.valueOf(category.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ExpenseCategory.OTHER;
        }
    }
}

