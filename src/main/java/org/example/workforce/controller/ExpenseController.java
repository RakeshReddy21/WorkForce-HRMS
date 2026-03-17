package org.example.workforce.controller;

import org.example.workforce.dto.*;
import org.example.workforce.model.Expense;
import org.example.workforce.service.ExpenseService;
import org.example.workforce.service.InvoiceParserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Employee Expense endpoints.
 * Employees can create, submit, and view their own expenses.
 * Also provides AI-powered invoice parsing.
 */
@RestController
@RequestMapping("/api/employee/expenses")
public class ExpenseController {

    private static final Logger log = LoggerFactory.getLogger(ExpenseController.class);

    @Autowired private ExpenseService expenseService;
    @Autowired private InvoiceParserService invoiceParserService;

    // Create a draft expense
    @PostMapping
    public ResponseEntity<Expense> createExpense(Authentication auth, @RequestBody ExpenseRequest request) {
        return ResponseEntity.ok(expenseService.createExpense(auth.getName(), request));
    }

    // Submit expense for approval
    @PatchMapping("/{id}/submit")
    public ResponseEntity<Expense> submitExpense(Authentication auth, @PathVariable Integer id) {
        return ResponseEntity.ok(expenseService.submitExpense(auth.getName(), id));
    }

    // Get my expenses
    @GetMapping
    public ResponseEntity<Page<Expense>> getMyExpenses(
            Authentication auth,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(expenseService.getMyExpenses(auth.getName(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    // Get single expense
    @GetMapping("/{id}")
    public ResponseEntity<Expense> getExpense(@PathVariable Integer id) {
        return ResponseEntity.ok(expenseService.getExpenseById(id));
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<Resource> getExpenseReceipt(Authentication auth, @PathVariable Integer id) {
        ExpenseService.ReceiptFileData receipt = expenseService.getExpenseReceipt(auth.getName(), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + receipt.fileName() + "\"")
                .contentType(MediaType.parseMediaType(receipt.contentType()))
                .body(receipt.resource());
    }

    // AI: Parse invoice text
    @PostMapping("/parse-invoice")
    public ResponseEntity<InvoiceParseResponse> parseInvoice(@RequestBody java.util.Map<String, String> body) {
        log.info("=== PARSE-INVOICE endpoint called ===");
        String invoiceText = body.get("invoiceText");
        return ResponseEntity.ok(invoiceParserService.parseInvoice(invoiceText));
    }

    // AI: Parse uploaded file (image or PDF)
    @PostMapping("/parse-file")
    public ResponseEntity<InvoiceParseResponse> parseFile(@RequestBody java.util.Map<String, String> body) {
        log.info("=== PARSE-FILE endpoint called === fileType={}, dataLength={}",
                body.get("fileType"),
                body.get("fileData") != null ? body.get("fileData").length() : "null");

        try {
            String base64Data = body.get("fileData");
            String fileType = body.get("fileType");
            InvoiceParseResponse result = invoiceParserService.parseUploadedFile(base64Data, fileType);
            log.info("=== PARSE-FILE result: success={}, vendor={}, amount={} ===",
                    result.isSuccess(), result.getVendorName(), result.getTotalAmount());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("=== PARSE-FILE EXCEPTION: {} ===", e.getMessage(), e);
            return ResponseEntity.ok(InvoiceParseResponse.builder()
                    .success(false)
                    .errorMessage("Server error: " + e.getMessage())
                    .build());
        }
    }
}
