package org.example.workforce.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.example.workforce.dto.InvoiceParseResponse;
import org.example.workforce.integration.OllamaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI-powered invoice/receipt parser.
 * Sends the invoice text (or a description of the uploaded image)
 * to Ollama and extracts structured fields.
 * Falls back to regex-based extraction when Ollama is not available.
 */
@Service
public class InvoiceParserService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceParserService.class);

    @Autowired
    private OllamaClient ollamaClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> VALID_CATEGORIES = Set.of(
            "TRAVEL", "MEALS", "ACCOMMODATION", "OFFICE_SUPPLIES", "EQUIPMENT",
            "SOFTWARE", "TRAINING", "CLIENT_ENTERTAINMENT", "COMMUNICATION",
            "MEDICAL", "TRANSPORTATION", "OTHER"
    );

    /**
     * Parse an uploaded file (image or PDF).
     * - PDF: extracts text with PDFBox, then parses with AI or regex fallback
     * - Image: uses LLaVA vision model
     */
    public InvoiceParseResponse parseUploadedFile(String base64Data, String fileType) {
        if (base64Data == null || base64Data.isBlank()) {
            return InvoiceParseResponse.builder()
                    .success(false).errorMessage("No file provided").build();
        }

        // Strip data URI prefix (e.g., "data:image/jpeg;base64," or "data:application/pdf;base64,")
        String cleanBase64 = base64Data.contains(",")
                ? base64Data.substring(base64Data.indexOf(",") + 1)
                : base64Data;

        boolean isPdf = fileType != null && fileType.toLowerCase().contains("pdf");

        if (isPdf) {
            return parsePdf(cleanBase64);
        } else {
            return parseImage(cleanBase64);
        }
    }

    /**
     * Extract text from PDF using PDFBox → enhanced regex first (instant), AI only if regex fails.
     * PRIORITY: regex is instant (~0ms) vs AI (~10-60s), so regex should handle 90%+ of invoices.
     */
    private InvoiceParseResponse parsePdf(String base64Pdf) {
        try {
            byte[] pdfBytes = Base64.getDecoder().decode(base64Pdf);
            String extractedText;

            try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
                PDFTextStripper stripper = new PDFTextStripper();
                extractedText = stripper.getText(doc);
            }

            if (extractedText == null || extractedText.isBlank()) {
                return InvoiceParseResponse.builder()
                        .success(false).errorMessage("Could not extract text from PDF. The PDF might be image-based.").build();
            }

            log.info("PDF text extracted ({} chars). Running instant regex parser first...", extractedText.length());
            log.debug("Extracted PDF text (first 1500 chars):\n{}", extractedText.substring(0, Math.min(extractedText.length(), 1500)));

            // Step 1: ALWAYS try regex first (instant — no network call)
            InvoiceParseResponse regexResult = regexParse(extractedText);
            if (regexResult.isSuccess()) {
                // Accept regex result if we got at least vendor OR amount (previously required amount)
                boolean hasUsefulData = regexResult.getTotalAmount() != null
                        || regexResult.getVendorName() != null
                        || regexResult.getInvoiceNumber() != null;
                if (hasUsefulData) {
                    log.info("✅ Regex parser extracted (instant): vendor={}, amount={}, date={}, invoice={}, items={}",
                            regexResult.getVendorName(), regexResult.getTotalAmount(),
                            regexResult.getInvoiceDate(), regexResult.getInvoiceNumber(),
                            regexResult.getItems() != null ? regexResult.getItems().size() : 0);
                    return regexResult;
                }
            }

            // Step 2: If regex failed/incomplete AND Ollama is available, try AI with TRUNCATED text
            if (ollamaClient.isAvailable()) {
                log.info("Regex incomplete — trying AI-powered extraction (truncated text)...");
                try {
                    // Truncate text to speed up AI processing — only send relevant parts
                    String truncatedText = truncateForAi(extractedText, 1500);
                    InvoiceParseResponse aiResult = parseInvoice(truncatedText);
                    if (aiResult.isSuccess()) {
                        log.info("AI extraction succeeded: vendor={}, amount={}", aiResult.getVendorName(), aiResult.getTotalAmount());
                        return aiResult;
                    }
                } catch (Exception e) {
                    log.warn("AI extraction failed: {}", e.getMessage());
                }
            }

            // Step 3: Return whatever regex got (even partial) — better than nothing
            if (regexResult.isSuccess()) {
                return regexResult;
            }

            // Nothing worked
            log.warn("Both regex and AI could not extract enough data from PDF.");
            return InvoiceParseResponse.builder()
                    .success(false)
                    .errorMessage("Could not extract data from this PDF. Please fill the form manually.")
                    .rawText(extractedText)
                    .build();

        } catch (IllegalArgumentException e) {
            log.error("Invalid base64 data: {}", e.getMessage());
            return InvoiceParseResponse.builder()
                    .success(false).errorMessage("Invalid file data. Please re-upload the PDF.").build();
        } catch (Exception e) {
            log.error("PDF processing failed: {}", e.getMessage(), e);
            return InvoiceParseResponse.builder()
                    .success(false).errorMessage("PDF processing failed: " + e.getMessage()).build();
        }
    }

    /**
     * Truncate text intelligently for AI — keep the header/footer (where totals/vendor info usually is)
     * and skip the middle (which is usually item details).
     */
    private String truncateForAi(String text, int maxChars) {
        if (text.length() <= maxChars) return text;

        // Keep first 60% (header, vendor, items) + last 40% (totals, payment info)
        int headerLen = (int) (maxChars * 0.6);
        int footerLen = maxChars - headerLen;
        String header = text.substring(0, headerLen);
        String footer = text.substring(text.length() - footerLen);
        return header + "\n...[truncated]...\n" + footer;
    }

    /** Send image to LLaVA vision model */
    private InvoiceParseResponse parseImage(String base64Image) {
        // Check Ollama availability first to fail fast
        if (!ollamaClient.isAvailable()) {
            log.warn("Ollama is not reachable — cannot process image invoice");
            return InvoiceParseResponse.builder()
                    .success(false)
                    .errorMessage("AI vision service is not available. Please upload a PDF instead for instant extraction, or start the Ollama service (ollama serve) and pull the vision model (ollama pull llava).")
                    .build();
        }

        try {
            // Compact vision prompt — fewer tokens = faster response from LLaVA
            String prompt = "Read this invoice/receipt. Reply with JSON only, no explanation.\n" +
                    "{\"title\":\"short title\",\"vendorName\":\"str\",\"invoiceNumber\":\"str\",\"invoiceDate\":\"YYYY-MM-DD\"," +
                    "\"totalAmount\":number,\"category\":\"TRAVEL|MEALS|ACCOMMODATION|OFFICE_SUPPLIES|EQUIPMENT|SOFTWARE|TRAINING|MEDICAL|TRANSPORTATION|OTHER\"," +
                    "\"description\":\"brief\",\"items\":[{\"description\":\"str\",\"amount\":number,\"quantity\":number}]}\nJSON:";

            String aiResponse = ollamaClient.generateWithImage(prompt, base64Image);

            if (aiResponse != null && !aiResponse.startsWith("Error")) {
                return parseAiResponse(aiResponse, "image-upload");
            }

            return InvoiceParseResponse.builder()
                    .success(false)
                    .errorMessage("Vision model could not read this image. Try uploading a PDF instead, or ensure the llava model is installed (ollama pull llava).")
                    .build();

        } catch (Exception e) {
            log.error("Image analysis failed: {}", e.getMessage());
            return InvoiceParseResponse.builder()
                    .success(false).errorMessage("Image analysis failed: " + e.getMessage()).build();
        }
    }

    /**
     * Parse invoice text using the text-only model (phi3).
     */
    public InvoiceParseResponse parseInvoice(String invoiceText) {
        if (invoiceText == null || invoiceText.isBlank()) {
            return InvoiceParseResponse.builder()
                    .success(false)
                    .errorMessage("No invoice text provided")
                    .build();
        }

        try {
            String prompt = buildExtractionPrompt(invoiceText);
            String aiResponse = ollamaClient.generate(prompt, 300);

            if (aiResponse == null || aiResponse.startsWith("Error")) {
                log.error("AI processing failed: {}", aiResponse);
                return InvoiceParseResponse.builder()
                        .success(false)
                        .errorMessage("AI processing failed: " + aiResponse)
                        .rawText(invoiceText)
                        .build();
            }

            return parseAiResponse(aiResponse, invoiceText);

        } catch (Exception e) {
            log.error("Failed to parse invoice: {}", e.getMessage());
            return InvoiceParseResponse.builder()
                    .success(false)
                    .errorMessage("Failed to parse invoice: " + e.getMessage())
                    .rawText(invoiceText)
                    .build();
        }
    }

    private String buildExtractionPrompt(String invoiceText) {
        // Compact prompt — fewer tokens = faster AI response
        return "Extract invoice data as JSON only. No explanation.\n" +
               "{\"title\":\"short title\",\"vendorName\":\"str\",\"invoiceNumber\":\"str\",\"invoiceDate\":\"YYYY-MM-DD\"," +
               "\"totalAmount\":number,\"category\":\"TRAVEL|MEALS|ACCOMMODATION|OFFICE_SUPPLIES|EQUIPMENT|SOFTWARE|TRAINING|MEDICAL|TRANSPORTATION|OTHER\"," +
               "\"description\":\"brief\",\"items\":[{\"description\":\"str\",\"amount\":number,\"quantity\":number}]}\n\n" +
               "Invoice:\n" + invoiceText + "\n\nJSON:";
    }

    private InvoiceParseResponse parseAiResponse(String aiResponse, String rawText) {
        try {
            // Try to extract JSON from the response
            String json = extractJson(aiResponse);
            log.info("Extracted JSON from AI response: {}", json.substring(0, Math.min(json.length(), 500)));
            JsonNode root = objectMapper.readTree(json);

            List<InvoiceParseResponse.ParsedItem> items = new ArrayList<>();
            if (root.has("items") && root.get("items").isArray()) {
                for (JsonNode itemNode : root.get("items")) {
                    items.add(InvoiceParseResponse.ParsedItem.builder()
                            .description(getTextOrNull(itemNode, "description"))
                            .amount(getDecimalOrNull(itemNode, "amount"))
                            .quantity(itemNode.has("quantity") ? itemNode.get("quantity").asInt(1) : 1)
                            .build());
                }
            }

            // Validate and normalize category
            String category = getTextOrNull(root, "category");
            if (category != null) {
                category = category.toUpperCase().replace(" ", "_");
                if (!VALID_CATEGORIES.contains(category)) {
                    category = guessCategory(rawText);
                }
            }

            return InvoiceParseResponse.builder()
                    .success(true)
                    .title(getTextOrNull(root, "title"))
                    .vendorName(getTextOrNull(root, "vendorName"))
                    .invoiceNumber(getTextOrNull(root, "invoiceNumber"))
                    .invoiceDate(getTextOrNull(root, "invoiceDate"))
                    .totalAmount(getDecimalOrNull(root, "totalAmount"))
                    .currency(root.has("currency") ? root.get("currency").asText("INR") : "INR")
                    .category(category)
                    .description(getTextOrNull(root, "description"))
                    .items(items)
                    .rawText(rawText)
                    .build();

        } catch (Exception e) {
            log.error("Could not parse AI response as JSON: {}", e.getMessage());
            return InvoiceParseResponse.builder()
                    .success(false)
                    .errorMessage("Could not parse AI response as structured data. Please fill fields manually.")
                    .rawText(rawText)
                    .build();
        }
    }

    // ─── Regex-based Fallback Parser ───────────────────────────────────────

    /**
     * Regex-based parser: extracts invoice data using pattern matching (instant, no AI needed).
     * Returns success=true even for partial data so the frontend auto-fills whatever was found.
     */
    private InvoiceParseResponse regexParse(String text) {
        log.info("Running regex-based extraction on text ({} chars)", text.length());

        String vendorName = extractVendorName(text);
        String invoiceNumber = extractInvoiceNumber(text);
        String invoiceDate = extractDate(text);
        BigDecimal totalAmount = extractTotalAmount(text);
        String category = guessCategory(text);
        List<InvoiceParseResponse.ParsedItem> items = extractLineItems(text);

        // Also try to extract order ID from filename-like patterns (ORDER_INVOICE_RD...)
        if (invoiceNumber == null) {
            Matcher orderMatcher = Pattern.compile("(?i)(?:ORDER[_\\s-]*(?:INVOICE)?[_\\s-]*)([A-Z0-9]{5,30})")
                    .matcher(text);
            if (orderMatcher.find()) {
                invoiceNumber = orderMatcher.group(1);
            }
        }

        // If items found but no total, sum the items
        if (totalAmount == null && !items.isEmpty()) {
            totalAmount = items.stream()
                    .map(i -> i.getAmount() != null ? i.getAmount().multiply(BigDecimal.valueOf(i.getQuantity() != null ? i.getQuantity() : 1)) : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        // Generate a title
        String title = generateTitle(vendorName, category, totalAmount);

        // Generate description from first few lines
        String description = generateDescription(text, vendorName);

        // Count how many fields we found
        int fieldsFound = 0;
        if (vendorName != null) fieldsFound++;
        if (invoiceNumber != null) fieldsFound++;
        if (invoiceDate != null) fieldsFound++;
        if (totalAmount != null) fieldsFound++;
        if (!items.isEmpty()) fieldsFound++;

        log.info("Regex extracted {} fields: vendor={}, invoice={}, date={}, amount={}, items={}",
                fieldsFound, vendorName, invoiceNumber, invoiceDate, totalAmount, items.size());

        // Return success if we found at least ONE useful field (was previously requiring multiple)
        if (fieldsFound == 0) {
            return InvoiceParseResponse.builder()
                    .success(false)
                    .errorMessage("Could not extract data from this PDF. Please fill the fields manually.")
                    .rawText(text)
                    .build();
        }

        return InvoiceParseResponse.builder()
                .success(true)
                .title(title)
                .vendorName(vendorName)
                .invoiceNumber(invoiceNumber)
                .invoiceDate(invoiceDate)
                .totalAmount(totalAmount)
                .currency("INR")
                .category(category)
                .description(description)
                .items(items)
                .rawText(text)
                .build();
    }

    private String generateDescription(String text, String vendorName) {
        // Extract meaningful first lines as description
        String[] lines = text.split("\\r?\\n");
        List<String> meaningful = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() > 5 && trimmed.length() < 100 && meaningful.size() < 3
                    && !trimmed.matches("^[\\d\\s./-]+$")
                    && (vendorName == null || !trimmed.equals(vendorName))) {
                meaningful.add(trimmed);
            }
        }
        return meaningful.isEmpty() ? null : String.join("; ", meaningful);
    }

    private String extractVendorName(String text) {
        // ★ PRIORITY 1: Known Indian brands/platforms — check FIRST (instant, most reliable)
        String lower = text.toLowerCase();
        if (lower.contains("rapido")) return "Rapido";
        if (lower.contains("amazon")) return "Amazon";
        if (lower.contains("flipkart")) return "Flipkart";
        if (lower.contains("myntra")) return "Myntra";
        if (lower.contains("swiggy")) return "Swiggy";
        if (lower.contains("zomato")) return "Zomato";
        if (lower.contains("uber eats")) return "Uber Eats";
        if (lower.contains("uber")) return "Uber";
        if (lower.contains("makemytrip") || lower.contains("make my trip")) return "MakeMyTrip";
        if (lower.contains("bigbasket") || lower.contains("big basket")) return "BigBasket";
        if (lower.contains("dunzo")) return "Dunzo";
        if (lower.contains("zepto")) return "Zepto";
        if (lower.contains("blinkit") || lower.contains("grofers")) return "Blinkit";
        if (lower.contains("ola cabs") || (lower.contains("ola") && containsAny(lower, "ride", "cab", "trip", "fare"))) return "Ola";
        if (lower.contains("phonepe")) return "PhonePe";
        if (lower.contains("paytm")) return "Paytm";
        if (lower.contains("meesho")) return "Meesho";
        if (lower.contains("nykaa")) return "Nykaa";
        if (lower.contains("jiomart")) return "JioMart";
        if (lower.contains("reliance")) return "Reliance";
        if (lower.contains("irctc")) return "IRCTC";
        if (lower.contains("redbus")) return "RedBus";
        if (lower.contains("cleartrip")) return "Cleartrip";
        if (lower.contains("goibibo")) return "Goibibo";
        if (lower.contains("dominos") || lower.contains("domino's")) return "Dominos";
        if (lower.contains("mcdonald")) return "McDonald's";
        if (lower.contains("starbucks")) return "Starbucks";

        // ★ PRIORITY 2: Explicit vendor label patterns (sold by, seller, vendor, etc.)
        String[] vendorPatterns = {
                "(?i)(?:sold\\s*by|seller\\s*(?:name)?|vendor\\s*(?:name)?|bill\\s*from|billed?\\s*by|merchant)\\s*[:\\-]?\\s*([^\\r\\n]+)",
                "(?i)(?:from|company|store|shop|restaurant|retailer)\\s*[:\\-]\\s*([^\\r\\n]+)"
        };
        // NOTE: "issued by", "shipped by", "supplied by" removed — they often match disclaimers

        for (String pattern : vendorPatterns) {
            Matcher m = Pattern.compile(pattern).matcher(text);
            if (m.find()) {
                String name = m.group(1).trim();
                name = name.split(",")[0].trim();
                name = name.replaceAll("\\s*\\(.*\\)\\s*$", "").trim();
                name = name.replaceAll("(?i)\\s*GST.*$", "").trim();
                // Reject if it looks like a disclaimer sentence (too long or contains "not by", "limited")
                if (!name.isEmpty() && name.length() >= 2 && name.length() < 60
                        && !name.toLowerCase().contains("not by")
                        && !name.toLowerCase().contains("private limited")
                        && !name.toLowerCase().contains("services")) {
                    return name;
                }
            }
        }

        // ★ PRIORITY 3: First meaningful line (common in receipts — company name at top)
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() > 3 && trimmed.length() < 80
                    && !trimmed.matches("(?i).*(?:invoice|receipt|bill\\s*no|tax|date|order\\s*id|order\\s*no|page|gst|total|amount|qty|hsn|payment|summary|\\d{2}/\\d{2}/\\d{4}).*")
                    && !trimmed.matches("^[\\d\\s.,/-]+$")
                    && !trimmed.matches("(?i)^(tax\\s+invoice|original|duplicate|copy|ride\\s*id|time|distance|duration).*")) {
                return trimmed;
            }
        }

        return null;
    }

    private String extractInvoiceNumber(String text) {
        // ★ Strategy: Try multiple patterns in priority order.
        //   PDFBox often interleaves two-column text (e.g., "Invoice No. Invoice Date...")
        //   so we need multiple approaches to reliably find the invoice number.

        Set<String> rejected = Set.of("details", "date", "summary", "amount", "total",
                "charge", "charges", "fee", "fees", "payment", "address", "name", "type",
                "number", "invoice", "bill", "receipt", "tax", "order", "ride", "booking",
                "category", "supply", "state", "using");

        // ── Strategy 1: "Invoice No." directly followed by alphanumeric ID (starts with digit) ──
        Matcher m1 = Pattern.compile("(?i)invoice\\s+no\\.?\\s*[:\\-]?\\s*([0-9][A-Za-z0-9]{4,49})").matcher(text);
        while (m1.find()) {
            String num = m1.group(1).trim();
            if (isValidInvoiceNumber(num, rejected)) return num;
        }

        // ── Strategy 2: Ride ID / Order ID / Booking ID ──
        Matcher m2 = Pattern.compile("(?i)(?:ride|order|booking|txn)\\s*id\\s*[:\\-]?\\s*([A-Za-z0-9\\-/.]{5,50})").matcher(text);
        while (m2.find()) {
            String num = m2.group(1).trim();
            if (isValidInvoiceNumber(num, rejected)) return num;
        }

        // ── Strategy 3: Generic "Bill/Receipt/Ref No." ──
        Matcher m3 = Pattern.compile("(?i)(?:bill|receipt|ref|reference|txn)\\s*(?:no|number|num|#|id)\\.?\\s*[:\\-]?\\s*([A-Za-z0-9\\-/]{3,50})").matcher(text);
        while (m3.find()) {
            String num = m3.group(1).trim();
            if (isValidInvoiceNumber(num, rejected)) return num;
        }

        // ── Strategy 4: Standalone alphanumeric IDs (e.g., "2526TN0091646633" — digits + uppercase) ──
        //   Common for Indian invoice numbers: starts with digits, contains uppercase letters
        Matcher m4 = Pattern.compile("(?m)^\\s*([0-9]{2,}[A-Z]+[A-Z0-9]{3,})\\s*$").matcher(text);
        while (m4.find()) {
            String num = m4.group(1).trim();
            if (num.length() >= 8 && num.length() <= 50) return num; // Long alphanumeric = likely invoice#
        }

        return null;
    }

    private boolean isValidInvoiceNumber(String num, Set<String> rejected) {
        return num.length() >= 3 && num.length() <= 50
                && num.matches(".*\\d.*")  // must have at least one digit
                && !rejected.contains(num.toLowerCase())
                && !num.matches("(?i)^(details|date|summary|amount|total|charge|invoice|bill|tax|Feb|Mar|Jan).*");
    }

    private String extractDate(String text) {
        // ★ FIX: Support ordinal suffixes (1st, 2nd, 3rd, 25th) in date patterns
        //   e.g., "Feb 25th 2026" or "25th Feb 2026"

        // Look near date keywords first (expand search to 80 chars for multi-word dates)
        String dateContext = text;
        Matcher dateLine = Pattern.compile("(?i)(?:date|dated|invoice\\s*date|bill\\s*date|order\\s*date|time\\s*of\\s*ride)[:\\s]*(.{0,80})").matcher(text);
        if (dateLine.find()) {
            dateContext = dateLine.group(1);
        }

        // Try to extract from the dateContext first, then fall back to full text
        String result = tryExtractDate(dateContext);
        if (result != null) return result;

        // If dateContext was a subset, try the full text
        if (!dateContext.equals(text)) {
            result = tryExtractDate(text);
        }
        return result;
    }

    private String tryExtractDate(String text) {
        // ── Pattern 1: "Month DDth YYYY" e.g., "Feb 25th 2026", "January 1st 2026" ──
        Matcher mOrd1 = Pattern.compile(
                "(?i)(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+(\\d{1,2})(?:st|nd|rd|th)?[,]?\\s+(\\d{4})"
        ).matcher(text);
        if (mOrd1.find()) {
            try {
                int day = Integer.parseInt(mOrd1.group(2));
                int month = monthToNumber(mOrd1.group(1));
                int year = Integer.parseInt(mOrd1.group(3));
                if (day >= 1 && day <= 31 && month >= 1 && month <= 12 && year >= 2000) {
                    return String.format("%d-%02d-%02d", year, month, day);
                }
            } catch (Exception ignored) {}
        }

        // ── Pattern 2: "DDth Month YYYY" e.g., "25th Feb 2026" ──
        Matcher mOrd2 = Pattern.compile(
                "(?i)(\\d{1,2})(?:st|nd|rd|th)?\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*[,]?\\s+(\\d{4})"
        ).matcher(text);
        if (mOrd2.find()) {
            try {
                int day = Integer.parseInt(mOrd2.group(1));
                int month = monthToNumber(mOrd2.group(2));
                int year = Integer.parseInt(mOrd2.group(3));
                if (day >= 1 && day <= 31 && month >= 1 && month <= 12 && year >= 2000) {
                    return String.format("%d-%02d-%02d", year, month, day);
                }
            } catch (Exception ignored) {}
        }

        // ── Pattern 3: DD-MM-YYYY or DD/MM/YYYY ──
        Matcher m1 = Pattern.compile("(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{4})").matcher(text);
        if (m1.find()) {
            try {
                int a = Integer.parseInt(m1.group(1));
                int b = Integer.parseInt(m1.group(2));
                int year = Integer.parseInt(m1.group(3));
                if (year >= 2000) {
                    if (a > 12) return String.format("%d-%02d-%02d", year, b, a);
                    else if (b > 12) return String.format("%d-%02d-%02d", year, a, b);
                    else return String.format("%d-%02d-%02d", year, b, a); // assume DD-MM-YYYY
                }
            } catch (Exception ignored) {}
        }

        // ── Pattern 4: YYYY-MM-DD ──
        Matcher m2 = Pattern.compile("(\\d{4})[/\\-](\\d{1,2})[/\\-](\\d{1,2})").matcher(text);
        if (m2.find()) {
            int year = Integer.parseInt(m2.group(1));
            if (year >= 2000) {
                return String.format("%d-%02d-%02d", year, Integer.parseInt(m2.group(2)), Integer.parseInt(m2.group(3)));
            }
        }

        return null;
    }

    private int monthToNumber(String monthStr) {
        return switch (monthStr.substring(0, 3).toLowerCase()) {
            case "jan" -> 1; case "feb" -> 2; case "mar" -> 3; case "apr" -> 4;
            case "may" -> 5; case "jun" -> 6; case "jul" -> 7; case "aug" -> 8;
            case "sep" -> 9; case "oct" -> 10; case "nov" -> 11; case "dec" -> 12;
            default -> -1;
        };
    }

    private BigDecimal extractTotalAmount(String text) {
        // ★ KEY FIX: Use short gaps (max 20 chars) between keyword and number to avoid
        //   matching addresses like "583/1A" when "amount" appears far away in the text.
        //   Also validate: skip numbers followed by "/" (addresses) or 6-digit pincodes.

        // Tier 1: Most specific "total" labels — short gap, require currency or decimal
        String[] tier1Patterns = {
                "(?i)(?:grand\\s*total|total\\s*amount|total\\s*payable|order\\s*total|invoice\\s*total|bill\\s*total|final\\s*amount|you\\s*pay)[^\\d₹]{0,20}(?:₹|Rs\\.?|INR)\\s*([\\d,]+\\.\\d{2})",
                "(?i)(?:grand\\s*total|total\\s*amount|total\\s*payable|order\\s*total|invoice\\s*total|bill\\s*total|final\\s*amount|you\\s*pay)[^\\d₹]{0,20}([\\d,]+\\.\\d{2})",
        };

        // Tier 2: "Total" at start of line with currency symbol
        String[] tier2Patterns = {
                "(?i)(?:^|\\n)\\s*total[^\\d₹]{0,15}(?:₹|Rs\\.?|INR)\\s*([\\d,]+\\.\\d{2})",
                "(?i)(?:^|\\n)\\s*total[^\\d₹]{0,15}([\\d,]+\\.\\d{2})",
        };

        // Tier 3: Currency symbol followed by amount (₹ 29.00)
        String tier3Pattern = "(?:₹|Rs\\.?)\\s*([\\d,]+\\.\\d{2})";

        // Try Tier 1 — take the MAX among specific "total" matches
        BigDecimal best = findBestAmount(text, tier1Patterns);
        if (best != null) return best;

        // Try Tier 2 — "Total" on its own line
        best = findBestAmount(text, tier2Patterns);
        if (best != null) return best;

        // Try Tier 3 — all currency-prefixed amounts, take the MAX
        BigDecimal maxAmount = null;
        Matcher m3 = Pattern.compile(tier3Pattern).matcher(text);
        while (m3.find()) {
            try {
                BigDecimal amt = parseAmount(m3.group(1));
                if (amt != null && isReasonableAmount(amt)) {
                    // Skip if this number is followed by "/" (address like 583/1A)
                    int endPos = m3.end();
                    if (endPos < text.length() && text.charAt(endPos) == '/') continue;
                    if (maxAmount == null || amt.compareTo(maxAmount) > 0) {
                        maxAmount = amt;
                    }
                }
            } catch (Exception ignored) {}
        }

        return maxAmount;
    }

    /** Find the best (largest) amount from an array of regex patterns */
    private BigDecimal findBestAmount(String text, String[] patterns) {
        BigDecimal best = null;
        for (String pattern : patterns) {
            Matcher m = Pattern.compile(pattern).matcher(text);
            while (m.find()) {
                try {
                    BigDecimal amt = parseAmount(m.group(1));
                    if (amt != null && isReasonableAmount(amt)) {
                        // Skip if number is followed by "/" (address like 583/1A)
                        int endPos = m.end();
                        if (endPos < text.length() && text.charAt(endPos) == '/') continue;
                        if (best == null || amt.compareTo(best) > 0) {
                            best = amt;
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (best != null) return best; // Stop at first tier that matches
        }
        return best;
    }

    private BigDecimal parseAmount(String amtStr) {
        if (amtStr == null || amtStr.isBlank()) return null;
        String cleaned = amtStr.replace(",", "").trim();
        if (cleaned.isEmpty()) return null;
        try {
            return new BigDecimal(cleaned);
        } catch (Exception e) {
            return null;
        }
    }

    private boolean isReasonableAmount(BigDecimal amt) {
        // Must be positive and less than 10 lakh (₹10,00,000) — skip pincodes like 641035
        return amt.compareTo(BigDecimal.ZERO) > 0 && amt.compareTo(new BigDecimal("1000000")) < 0;
    }

    private String guessCategory(String text) {
        String lower = text.toLowerCase();

        if (containsAny(lower, "flight", "airline", "airport", "travel", "ticket", "railway", "train", "irctc"))
            return "TRAVEL";
        if (containsAny(lower, "restaurant", "food", "meal", "lunch", "dinner", "breakfast", "cafe", "catering", "zomato", "swiggy"))
            return "MEALS";
        if (containsAny(lower, "hotel", "accommodation", "stay", "room", "lodge", "resort", "oyo", "airbnb"))
            return "ACCOMMODATION";
        if (containsAny(lower, "stationery", "office supply", "office supplies", "pen", "paper", "printer"))
            return "OFFICE_SUPPLIES";
        if (containsAny(lower, "laptop", "computer", "monitor", "keyboard", "mouse", "equipment", "hardware"))
            return "EQUIPMENT";
        if (containsAny(lower, "software", "license", "subscription", "saas", "aws", "azure", "cloud"))
            return "SOFTWARE";
        if (containsAny(lower, "training", "course", "seminar", "workshop", "conference", "certification"))
            return "TRAINING";
        if (containsAny(lower, "hospital", "medical", "pharmacy", "medicine", "doctor", "clinic", "health"))
            return "MEDICAL";
        if (containsAny(lower, "uber", "ola", "taxi", "cab", "fuel", "petrol", "diesel", "parking", "toll", "metro", "bus", "rapido", "ride charge", "ride id", "captain"))
            return "TRANSPORTATION";
        if (containsAny(lower, "phone", "mobile", "internet", "broadband", "telecom", "airtel", "jio"))
            return "COMMUNICATION";
        if (containsAny(lower, "amazon", "flipkart", "myntra", "online", "shopping", "ecommerce"))
            return "OTHER";

        return "OTHER";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private List<InvoiceParseResponse.ParsedItem> extractLineItems(String text) {
        List<InvoiceParseResponse.ParsedItem> items = new ArrayList<>();
        Set<String> seenDescs = new HashSet<>();

        // ★ Exclusion: tax lines, addresses, pincodes, payment info, metadata
        // NOTE: "ride charge" and "booking fee" are REAL bill items — do NOT exclude them!
        Pattern exclusionPattern = Pattern.compile(
                "(?i)(?:^total$|total\\s*amount|total\\s*payable|grand\\s*total|final\\s*amount|" +
                "sub\\s*total|subtotal|sub-total|^total\\s*₹|" +
                "gst|cgst|sgst|igst|cess|" +
                "discount|coupon|promo|saving|cashback|round\\s*off|" +
                "balance|paid|you\\s*pay|net\\s*amount|inclusive|" +
                "tax\\s*category|place\\s*of\\s*supply|gst\\s*number|vehicle\\s*number|captain\\s*name|customer\\s*name|" +
                "address|india|tamil\\s*nadu|karnataka|maharashtra|andhra|telangana|" +
                "pin\\s*code|state\\s|invoice\\s*date|invoice\\s*no|ride\\s*id|time\\s*of|" +
                "qr\\s*pay|upi|wallet|passengers\\s*n\\.?e\\.?c|n\\.?e\\.?c\\.?|" +
                "duration|distance|kms|mins|minutes|kilometers)");

        // ★ FIX: Skip descriptions that start with currency symbols (₹5.48, Rs.100)
        Pattern currencyDescPattern = Pattern.compile("^(?:₹|Rs\\.?|INR)\\s*\\d");

        // ★ FIX: Skip descriptions that look like pincodes or numbers only
        Pattern numericDescPattern = Pattern.compile("^[\\d\\s.,₹/]+$");

        // Patterns for line items (description + amount on same line)
        String[] itemPatterns = {
                // 1. "Ride Charge ₹ 23.52" — description followed by ₹ amount (1+ space is enough)
                "(?m)^\\s*(?:\\d+[.)]?\\s+)?(.{4,80})\\s+(?:₹|Rs\\.?|INR)\\s*([\\d,]+\\.\\d{2})\\s*$",
                // 2. "Item description  150.00" — no currency, requires 2+ spaces + decimal
                "(?m)^\\s*(?:\\d+[.)]?\\s+)?(.{5,70})\\s{2,}([\\d,]+\\.\\d{2})\\s*$",
                // 3. Numbered: "1  Item description  2  ₹150.00"
                "(?m)^\\s*\\d+[.)]?\\s+(.{3,80})\\s+(\\d+)\\s+(?:₹|Rs\\.?|INR)?\\s*([\\d,]+\\.\\d{2})\\s*$",
                // 4. "Item Name  Qty  Rate  Amount" — last number is amount
                "(?m)^\\s*(?:\\d+[.)]?\\s+)?(.{3,60})\\s+(\\d+)\\s+[\\d,.]+\\s+(?:₹|Rs\\.?|INR)?\\s*([\\d,]+\\.\\d{0,2})\\s*$"
        };

        for (String pattern : itemPatterns) {
            Matcher m = Pattern.compile(pattern).matcher(text);
            while (m.find() && items.size() < 20) {
                try {
                    String desc = m.group(1).trim();

                    // ★ Skip exclusions
                    if (exclusionPattern.matcher(desc).find()) continue;
                    if (currencyDescPattern.matcher(desc).find()) continue;
                    if (numericDescPattern.matcher(desc).matches()) continue;
                    if (desc.length() < 3 || seenDescs.contains(desc.toLowerCase())) continue;

                    // Skip header-like lines (but NOT item descriptions containing "charge" or "fee")
                    if (desc.matches("(?i).*(?:description|item\\s*name|particulars|s\\.?\\s*no|qty|quantity|^rate$|^price$|^amount$|hsn|sac|bill\\s*details|payment\\s*summary).*"))
                        continue;

                    // Skip if description is just a number/pincode (641035, 996419)
                    if (desc.matches("^\\d{4,}.*")) continue;

                    int qty = 1;
                    String amtStr;
                    if (m.groupCount() >= 3 && m.group(3) != null) {
                        try { qty = Integer.parseInt(m.group(2)); } catch (Exception ignored) {}
                        amtStr = m.group(3).replace(",", "");
                    } else {
                        amtStr = m.group(2).replace(",", "");
                    }

                    BigDecimal amount = new BigDecimal(amtStr);
                    if (amount.compareTo(BigDecimal.ZERO) > 0 && amount.compareTo(new BigDecimal("999999")) < 0) {
                        seenDescs.add(desc.toLowerCase());
                        items.add(InvoiceParseResponse.ParsedItem.builder()
                                .description(desc)
                                .amount(amount)
                                .quantity(qty)
                                .build());
                    }
                } catch (Exception ignored) {}
            }
            if (!items.isEmpty()) break;
        }

        return items;
    }

    private String generateTitle(String vendorName, String category, BigDecimal amount) {
        List<String> parts = new ArrayList<>();
        if (vendorName != null && !vendorName.isEmpty()) {
            parts.add(vendorName);
        }
        if (category != null && !category.equals("OTHER")) {
            parts.add(category.replace("_", " ").substring(0, 1).toUpperCase()
                    + category.replace("_", " ").substring(1).toLowerCase());
        }
        if (!parts.isEmpty()) {
            return String.join(" — ", parts);
        }
        if (amount != null) {
            return "Expense ₹" + amount.toPlainString();
        }
        return "Expense";
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private String extractJson(String text) {
        // Find the first { and last } to extract JSON
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String getTextOrNull(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            String val = node.get(field).asText();
            return "null".equalsIgnoreCase(val) || val.isBlank() ? null : val;
        }
        return null;
    }

    private BigDecimal getDecimalOrNull(JsonNode node, String field) {
        if (node.has(field) && !node.get(field).isNull()) {
            if (node.get(field).isNumber()) {
                return node.get(field).decimalValue();
            }
            // Try parsing string as number
            try {
                String val = node.get(field).asText().replaceAll("[^\\d.]", "");
                if (!val.isEmpty()) {
                    return new BigDecimal(val);
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}
