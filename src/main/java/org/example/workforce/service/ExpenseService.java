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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class ExpenseService {

    @Autowired private ExpenseRepository expenseRepository;
    @Autowired private EmployeeService employeeService;
    @Autowired private NotificationService notificationService;
    @Value("${expense.receipts.dir:uploads/expense-receipts}")
    private String expenseReceiptsDir;

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

        Expense saved = expenseRepository.save(expense);
        if (request.getReceiptBase64() != null && !request.getReceiptBase64().isBlank()) {
            String filePath = saveReceiptFile(saved.getExpenseId(), request.getReceiptBase64(), request.getReceiptFileName());
            saved.setReceiptUrl(filePath);
            if (request.getReceiptFileName() != null && !request.getReceiptFileName().isBlank()) {
                saved.setReceiptFileName(request.getReceiptFileName());
            }
            saved = expenseRepository.save(saved);
        }
        initializeExpenseAssociations(saved);
        return saved;
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

        initializeExpenseAssociations(saved);
        return saved;
    }

    // ─── Employee: My Expenses ───
    @Transactional(readOnly = true)
    public Page<Expense> getMyExpenses(String email, Pageable pageable) {
        Employee employee = employeeService.getEmployeeByEmail(email);
        Page<Expense> page = expenseRepository.findByEmployeeEmployeeId(employee.getEmployeeId(), pageable);
        page.getContent().forEach(this::initializeExpenseAssociations);
        return page;
    }

    // ─── Manager: Team Expenses pending approval ───
    @Transactional(readOnly = true)
    public Page<Expense> getTeamExpenses(String email, Pageable pageable) {
        Employee manager = employeeService.getEmployeeByEmail(email);
        Page<Expense> page = expenseRepository.findTeamExpensesByStatus(
                manager.getEmployeeId(), ExpenseStatus.SUBMITTED, pageable);
        page.getContent().forEach(this::initializeExpenseAssociations);
        return page;
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

        Expense saved = expenseRepository.save(expense);
        initializeExpenseAssociations(saved);
        return saved;
    }

    // ─── Finance/Admin: Expenses pending finance approval ───
    @Transactional(readOnly = true)
    public Page<Expense> getFinancePendingExpenses(Pageable pageable) {
        Page<Expense> page = expenseRepository.findByStatus(ExpenseStatus.MANAGER_APPROVED, pageable);
        page.getContent().forEach(this::initializeExpenseAssociations);
        return page;
    }

    // ─── Finance/Admin: All expenses ───
    @Transactional(readOnly = true)
    public Page<Expense> getAllExpenses(ExpenseStatus status, Pageable pageable) {
        Page<Expense> page;
        if (status != null) {
            page = expenseRepository.findByStatus(status, pageable);
        } else {
            page = expenseRepository.findAll(pageable);
        }
        page.getContent().forEach(this::initializeExpenseAssociations);
        return page;
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

        Expense saved = expenseRepository.save(expense);
        initializeExpenseAssociations(saved);
        return saved;
    }

    // ─── Helpers ───
    @Transactional(readOnly = true)
    public Expense getExpenseById(Integer id) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + id));
        initializeExpenseAssociations(expense);
        return expense;
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

    private void initializeExpenseAssociations(Expense expense) {
        if (expense != null && expense.getItems() != null) {
            expense.getItems().size();
        }
    }

    public ReceiptFileData getExpenseReceipt(String email, Integer expenseId) {
        Employee requester = employeeService.getEmployeeByEmail(email);
        Expense expense = getExpenseById(expenseId);

        boolean isOwner = expense.getEmployee().getEmployeeId().equals(requester.getEmployeeId());
        boolean isReviewer = requester.getRole() == Role.ADMIN || requester.getRole() == Role.MANAGER;
        if (!isOwner && !isReviewer) {
            throw new BadRequestException("You are not authorized to view this receipt.");
        }
        if (expense.getReceiptUrl() == null || expense.getReceiptUrl().isBlank()) {
            throw new ResourceNotFoundException("No receipt uploaded for this expense.");
        }

        try {
            Path path = Paths.get(expense.getReceiptUrl()).toAbsolutePath().normalize();
            if (!Files.exists(path)) {
                throw new ResourceNotFoundException("Receipt file not found on server.");
            }

            Resource resource = new UrlResource(path.toUri());
            String contentType = Files.probeContentType(path);
            if (contentType == null || contentType.isBlank()) {
                contentType = "application/octet-stream";
            }
            String fileName = (expense.getReceiptFileName() != null && !expense.getReceiptFileName().isBlank())
                    ? expense.getReceiptFileName()
                    : path.getFileName().toString();
            return new ReceiptFileData(resource, contentType, fileName);
        } catch (MalformedURLException e) {
            throw new BadRequestException("Invalid receipt file path.");
        } catch (IOException e) {
            throw new BadRequestException("Unable to read receipt file.");
        }
    }

    private String saveReceiptFile(Integer expenseId, String receiptBase64, String originalFileName) {
        try {
            String payload = receiptBase64.trim();
            String mimeType = "application/octet-stream";

            if (payload.startsWith("data:")) {
                int commaIndex = payload.indexOf(',');
                if (commaIndex <= 0) {
                    throw new BadRequestException("Invalid receipt payload format.");
                }
                String metadata = payload.substring(5, commaIndex);
                if (!metadata.contains("base64")) {
                    throw new BadRequestException("Receipt payload must be base64 encoded.");
                }
                int semicolonIndex = metadata.indexOf(';');
                if (semicolonIndex > 0) {
                    mimeType = metadata.substring(0, semicolonIndex);
                }
                payload = payload.substring(commaIndex + 1);
            }

            byte[] fileBytes = Base64.getDecoder().decode(payload);
            if (fileBytes.length == 0) {
                throw new BadRequestException("Uploaded receipt file is empty.");
            }
            if (fileBytes.length > 10 * 1024 * 1024) {
                throw new BadRequestException("Receipt file size exceeds 10 MB.");
            }

            String extension = resolveFileExtension(originalFileName, mimeType);
            Path receiptsDir = Paths.get(expenseReceiptsDir).toAbsolutePath().normalize();
            Files.createDirectories(receiptsDir);

            String storedName = "expense-" + expenseId + "-" + System.currentTimeMillis() + extension;
            Path storedPath = receiptsDir.resolve(storedName);
            Files.write(storedPath, fileBytes);
            return storedPath.toString();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid base64 receipt data.");
        } catch (IOException e) {
            throw new BadRequestException("Unable to store uploaded receipt.");
        }
    }

    private String resolveFileExtension(String originalFileName, String mimeType) {
        if (originalFileName != null) {
            int dotIndex = originalFileName.lastIndexOf('.');
            if (dotIndex >= 0 && dotIndex < originalFileName.length() - 1) {
                String ext = originalFileName.substring(dotIndex).trim();
                if (ext.length() <= 10) {
                    return ext;
                }
            }
        }

        return switch (mimeType.toLowerCase()) {
            case "image/png" -> ".png";
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "application/pdf" -> ".pdf";
            default -> ".bin";
        };
    }

    public record ReceiptFileData(Resource resource, String contentType, String fileName) {}
}

