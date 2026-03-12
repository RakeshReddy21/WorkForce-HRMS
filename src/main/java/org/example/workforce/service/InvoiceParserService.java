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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

    /** Extract text from PDF using PDFBox, then parse with regex (instant) */
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

            log.info("PDF text extracted ({} chars). Running instant regex parser...", extractedText.length());
            log.debug("Extracted PDF text:\n{}", extractedText.substring(0, Math.min(extractedText.length(), 2000)));

            // Use regex-based extraction (instant — no AI model needed)
            InvoiceParseResponse result = regexParse(extractedText);
            if (result.isSuccess()) {
                log.info("Regex parser extracted data successfully: vendor={}, amount={}, date={}, invoice={}",
                        result.getVendorName(), result.getTotalAmount(), result.getInvoiceDate(), result.getInvoiceNumber());
                return result;
            }

            log.warn("Regex parser could not extract enough data. Returning partial result.");
            return result;

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

    /** Send image to LLaVA vision model */
    private InvoiceParseResponse parseImage(String base64Image) {
        try {
            String prompt = """
                    Read this invoice/receipt image carefully and extract ALL data from it.
                    Respond ONLY with a valid JSON object. No explanation, no markdown, no code blocks.
                    
                    {
                      "title": "short descriptive title for this expense e.g. 'Office Supplies from Amazon' or 'Lunch at Restaurant Name'",
                      "vendorName": "vendor/restaurant/shop/company name",
                      "invoiceNumber": "bill/invoice/order number if visible",
                      "invoiceDate": "YYYY-MM-DD format",
                      "totalAmount": numeric_value_without_currency_symbol,
                      "currency": "INR",
                      "category": "ONE of: TRAVEL, MEALS, ACCOMMODATION, OFFICE_SUPPLIES, EQUIPMENT, SOFTWARE, TRAINING, MEDICAL, TRANSPORTATION, OTHER",
                      "description": "brief description of what was purchased",
                      "items": [{"description": "item name", "amount": numeric_value, "quantity": number}]
                    }
                    
                    JSON:""";

            String aiResponse = ollamaClient.generateWithImage(prompt, base64Image);

            if (aiResponse != null && !aiResponse.startsWith("Error")) {
                return parseAiResponse(aiResponse, "image-upload");
            }

            return InvoiceParseResponse.builder()
                    .success(false)
                    .errorMessage("Vision model not available. Install it with: ollama pull llava")
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
            String aiResponse = ollamaClient.generate(prompt);

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
        return """
                You are an invoice data extraction assistant. Extract structured data from the following invoice/receipt text.
                
                IMPORTANT RULES:
                1. Respond ONLY with a valid JSON object. No explanation, no markdown, no code blocks.
                2. Extract ALL available fields. Use null for missing fields.
                3. For totalAmount, extract ONLY the numeric value (no currency symbols).
                4. For invoiceDate, use YYYY-MM-DD format.
                5. Generate a concise, descriptive title for this expense.
                
                JSON format:
                {
                  "title": "short descriptive expense title e.g. 'Office Supplies from Amazon'",
                  "vendorName": "string or null",
                  "invoiceNumber": "string or null",
                  "invoiceDate": "YYYY-MM-DD or null",
                  "totalAmount": number or null,
                  "currency": "INR or detected currency",
                  "category": "ONE of: TRAVEL, MEALS, ACCOMMODATION, OFFICE_SUPPLIES, EQUIPMENT, SOFTWARE, TRAINING, CLIENT_ENTERTAINMENT, COMMUNICATION, MEDICAL, TRANSPORTATION, OTHER",
                  "description": "brief description of what was purchased or null",
                  "items": [{"description": "string", "amount": number, "quantity": number}]
                }
                
                Invoice text:
                \"\"\"""" + "\n" + invoiceText + """
                \"\"\"
                
                JSON:""";
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
     */
    private InvoiceParseResponse regexParse(String text) {
        log.info("Running regex-based extraction on text ({} chars)", text.length());

        String vendorName = extractVendorName(text);
        String invoiceNumber = extractInvoiceNumber(text);
        String invoiceDate = extractDate(text);
        BigDecimal totalAmount = extractTotalAmount(text);
        String category = guessCategory(text);
        List<InvoiceParseResponse.ParsedItem> items = extractLineItems(text);

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

        boolean hasData = vendorName != null || invoiceNumber != null || totalAmount != null || invoiceDate != null;

        if (!hasData && items.isEmpty()) {
            return InvoiceParseResponse.builder()
                    .success(false)
                    .errorMessage("Could not extract enough data from this PDF. Please fill the fields manually.")
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
        // Pattern 1: Explicit vendor labels
        String[] vendorPatterns = {
                "(?i)(?:sold\\s*by|seller\\s*(?:name)?|vendor\\s*(?:name)?|bill\\s*from|billed?\\s*by|merchant|shipped?\\s*by)\\s*[:\\-]?\\s*([^\\r\\n]+)",
                "(?i)(?:from|company|store|shop|restaurant)\\s*[:\\-]\\s*([^\\r\\n]+)"
        };

        for (String pattern : vendorPatterns) {
            Matcher m = Pattern.compile(pattern).matcher(text);
            if (m.find()) {
                String name = m.group(1).trim();
                // Clean up: remove trailing punctuation, GST numbers, addresses
                name = name.split(",")[0].trim();
                name = name.replaceAll("\\s*\\(.*\\)\\s*$", "").trim();
                if (!name.isEmpty() && name.length() >= 2 && name.length() < 100) {
                    return name;
                }
            }
        }

        // Pattern 2: Common ecommerce platforms
        String lower = text.toLowerCase();
        if (lower.contains("amazon")) return "Amazon";
        if (lower.contains("flipkart")) return "Flipkart";
        if (lower.contains("myntra")) return "Myntra";
        if (lower.contains("swiggy")) return "Swiggy";
        if (lower.contains("zomato")) return "Zomato";
        if (lower.contains("uber")) return "Uber";
        if (lower.contains("makemytrip") || lower.contains("make my trip")) return "MakeMyTrip";
        if (lower.contains("bigbasket")) return "BigBasket";

        // Pattern 3: First meaningful line (common in receipts)
        String[] lines = text.split("\\r?\\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.length() > 3 && trimmed.length() < 80
                    && !trimmed.matches("(?i).*(?:invoice|receipt|bill\\s*no|tax|date|order\\s*id|order\\s*no|page|gst|total|amount|qty|hsn|\\d{2}/\\d{2}/\\d{4}).*")
                    && !trimmed.matches("^[\\d\\s.,/-]+$")
                    && !trimmed.matches("(?i)^(tax\\s+invoice|original|duplicate|copy).*")) {
                return trimmed;
            }
        }

        return null;
    }

    private String extractInvoiceNumber(String text) {
        String[] patterns = {
                "(?i)(?:invoice|inv|bill|receipt|order|reference|ref|transaction|txn)\\s*(?:no|number|num|#|id)?\\s*[.:\\-]?\\s*([A-Za-z0-9\\-/]+)",
                "(?i)(?:order\\s*id)\\s*[:\\-]?\\s*([A-Za-z0-9\\-/.]+)"
        };
        for (String pattern : patterns) {
            Matcher m = Pattern.compile(pattern).matcher(text);
            if (m.find()) {
                String num = m.group(1).trim();
                if (num.length() >= 3 && num.length() <= 50) {
                    return num;
                }
            }
        }
        return null;
    }

    private String extractDate(String text) {
        // Try common date formats
        String[] datePatterns = {
                // DD-MM-YYYY or DD/MM/YYYY
                "(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{4})",
                // YYYY-MM-DD
                "(\\d{4})[/\\-](\\d{1,2})[/\\-](\\d{1,2})",
                // DD Mon YYYY or DD Month YYYY
                "(\\d{1,2})\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+(\\d{4})",
                // Month DD, YYYY
                "(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+(\\d{1,2}),?\\s+(\\d{4})"
        };

        // Look near date keywords first
        String dateContext = text;
        Matcher dateLine = Pattern.compile("(?i)(?:date|dated|invoice\\s*date|bill\\s*date|order\\s*date)[:\\s]*(.{0,50})").matcher(text);
        if (dateLine.find()) {
            dateContext = dateLine.group(1);
        }

        // DD-MM-YYYY or DD/MM/YYYY
        Matcher m1 = Pattern.compile("(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{4})").matcher(dateContext);
        if (m1.find()) {
            try {
                int a = Integer.parseInt(m1.group(1));
                int b = Integer.parseInt(m1.group(2));
                int year = Integer.parseInt(m1.group(3));
                // Determine if DD-MM-YYYY or MM-DD-YYYY
                if (a > 12) { // DD-MM-YYYY
                    return String.format("%d-%02d-%02d", year, b, a);
                } else if (b > 12) { // MM-DD-YYYY
                    return String.format("%d-%02d-%02d", year, a, b);
                } else { // Assume DD-MM-YYYY (Indian format)
                    return String.format("%d-%02d-%02d", year, b, a);
                }
            } catch (Exception ignored) {}
        }

        // YYYY-MM-DD
        Matcher m2 = Pattern.compile("(\\d{4})[/\\-](\\d{1,2})[/\\-](\\d{1,2})").matcher(dateContext);
        if (m2.find()) {
            return String.format("%s-%02d-%02d",
                    m2.group(1), Integer.parseInt(m2.group(2)), Integer.parseInt(m2.group(3)));
        }

        // DD Month YYYY
        Matcher m3 = Pattern.compile("(?i)(\\d{1,2})\\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+(\\d{4})").matcher(dateContext);
        if (m3.find()) {
            try {
                String dateStr = m3.group(0);
                DateTimeFormatter[] formatters = {
                        DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH),
                        DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
                };
                for (DateTimeFormatter fmt : formatters) {
                    try {
                        LocalDate date = LocalDate.parse(dateStr.trim(), fmt);
                        return date.toString();
                    } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }

        // If not found near keywords, search entire text
        if (dateContext.equals(text)) return null;

        Matcher m4 = Pattern.compile("(\\d{1,2})[/\\-](\\d{1,2})[/\\-](\\d{4})").matcher(text);
        if (m4.find()) {
            try {
                int a = Integer.parseInt(m4.group(1));
                int b = Integer.parseInt(m4.group(2));
                int year = Integer.parseInt(m4.group(3));
                if (a > 12) return String.format("%d-%02d-%02d", year, b, a);
                else return String.format("%d-%02d-%02d", year, b, a);
            } catch (Exception ignored) {}
        }

        return null;
    }

    private BigDecimal extractTotalAmount(String text) {
        // Look for total/grand total/amount due/net amount patterns
        String[] totalPatterns = {
                "(?i)(?:grand\\s*total|total\\s*amount|amount\\s*(?:due|payable)|net\\s*(?:total|amount|payable)|total\\s*(?:incl|including|inc))[^\\d]*([\\d,]+\\.?\\d*)",
                "(?i)(?:^|\\n)\\s*total[^\\d]*([\\d,]+\\.?\\d*)",
                "(?i)(?:amount|amt|sum)[^\\d]*([\\d,]+\\.?\\d*)",
                "(?i)(?:₹|Rs\\.?|INR|USD|\\$)\\s*([\\d,]+\\.?\\d*)"
        };

        BigDecimal maxAmount = null;

        // First try specific "total" patterns
        for (int i = 0; i < totalPatterns.length - 1; i++) {
            Matcher m = Pattern.compile(totalPatterns[i]).matcher(text);
            while (m.find()) {
                try {
                    String amtStr = m.group(1).replace(",", "");
                    BigDecimal amt = new BigDecimal(amtStr);
                    if (amt.compareTo(BigDecimal.ZERO) > 0) {
                        if (maxAmount == null || amt.compareTo(maxAmount) > 0) {
                            maxAmount = amt;
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        if (maxAmount != null) return maxAmount;

        // Fallback: find the largest currency amount
        Matcher currencyMatcher = Pattern.compile("(?i)(?:₹|Rs\\.?|INR|\\$|USD)?\\s*([\\d,]+\\.\\d{2})").matcher(text);
        while (currencyMatcher.find()) {
            try {
                String amtStr = currencyMatcher.group(1).replace(",", "");
                BigDecimal amt = new BigDecimal(amtStr);
                if (amt.compareTo(BigDecimal.ZERO) > 0 && amt.compareTo(new BigDecimal("9999999")) < 0) {
                    if (maxAmount == null || amt.compareTo(maxAmount) > 0) {
                        maxAmount = amt;
                    }
                }
            } catch (Exception ignored) {}
        }

        return maxAmount;
    }

    private String guessCategory(String text) {
        String lower = text.toLowerCase();

        if (containsAny(lower, "flight", "airline", "airport", "travel", "ticket", "booking", "railway", "train"))
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
        if (containsAny(lower, "uber", "ola", "taxi", "cab", "fuel", "petrol", "diesel", "parking", "toll", "metro", "bus"))
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

        // Look for patterns like: "item description    qty    amount"
        // or "1. Item name   ₹100"
        Matcher itemMatcher = Pattern.compile(
                "(?m)^\\s*(?:\\d+[.)]?\\s+)?(.{5,50})\\s+(?:(\\d+)\\s+)?(?:₹|Rs\\.?|INR)?\\s*([\\d,]+\\.?\\d{0,2})\\s*$"
        ).matcher(text);

        while (itemMatcher.find() && items.size() < 20) {
            try {
                String desc = itemMatcher.group(1).trim();
                int qty = itemMatcher.group(2) != null ? Integer.parseInt(itemMatcher.group(2)) : 1;
                String amtStr = itemMatcher.group(3).replace(",", "");
                BigDecimal amount = new BigDecimal(amtStr);

                if (amount.compareTo(BigDecimal.ZERO) > 0
                        && !desc.toLowerCase().matches(".*(?:total|subtotal|tax|gst|discount|shipping).*")) {
                    items.add(InvoiceParseResponse.ParsedItem.builder()
                            .description(desc)
                            .amount(amount)
                            .quantity(qty)
                            .build());
                }
            } catch (Exception ignored) {}
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
