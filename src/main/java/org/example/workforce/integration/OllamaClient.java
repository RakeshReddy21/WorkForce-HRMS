package org.example.workforce.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OllamaClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaClient.class);

    @Value("${ollama.base-url:http://localhost:11434}")
    private String baseUrl;
    @Value("${ollama.model:phi3}")
    private String model;
    @Value("${ollama.vision-model:llava}")
    private String visionModel;
    @Value("${ollama.timeout:60000}")
    private int timeout;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ─── Availability cache (avoids 3s timeout on every request) ───
    private volatile Boolean cachedAvailable = null;
    private volatile long cachedAt = 0;
    private static final long CACHE_TTL_MS = 60_000; // 60 seconds

    // ─── Cached RestTemplate (avoid re-creation on every call) ───
    private volatile RestTemplate cachedRestTemplate;

    private RestTemplate getRestTemplate() {
        if (cachedRestTemplate == null) {
            synchronized (this) {
                if (cachedRestTemplate == null) {
                    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
                    factory.setConnectTimeout(java.time.Duration.ofMillis(5000));
                    factory.setReadTimeout(java.time.Duration.ofMillis(timeout));
                    cachedRestTemplate = new RestTemplate(factory);
                }
            }
        }
        return cachedRestTemplate;
    }

    /** Check if Ollama is reachable (cached for 60 seconds) */
    public boolean isAvailable() {
        long now = System.currentTimeMillis();
        if (cachedAvailable != null && (now - cachedAt) < CACHE_TTL_MS) {
            return cachedAvailable;
        }
        boolean result = checkAvailability();
        cachedAvailable = result;
        cachedAt = now;
        return result;
    }

    /** Force-refresh the availability cache */
    public void refreshAvailability() {
        cachedAvailable = null;
        cachedAt = 0;
    }

    private boolean checkAvailability() {
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(java.time.Duration.ofMillis(2000));
            factory.setReadTimeout(java.time.Duration.ofMillis(2000));
            RestTemplate quickTemplate = new RestTemplate(factory);
            ResponseEntity<String> response = quickTemplate.getForEntity(baseUrl + "/api/tags", String.class);
            boolean ok = response.getStatusCode().is2xxSuccessful();
            if (ok) log.info("Ollama is available at {}", baseUrl);
            return ok;
        } catch (Exception e) {
            log.debug("Ollama not reachable at {}: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    /** Text-only generation (phi3) — optimized for speed */
    public String generate(String prompt) {
        return generate(prompt, 150);
    }

    /** Text-only generation with configurable max tokens */
    public String generate(String prompt, int maxTokens) {
        String url = baseUrl + "/api/generate";
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("stream", false);

        // ── Speed optimizations ──
        Map<String, Object> options = new HashMap<>();
        options.put("num_predict", maxTokens);   // limit output tokens → faster
        options.put("temperature", 0);            // deterministic → no sampling overhead
        options.put("top_k", 1);                  // greedy decoding → fastest
        options.put("num_ctx", 1024);             // smaller context window → faster
        options.put("repeat_penalty", 1.0);       // no repeat penalty → faster
        options.put("num_thread", 4);             // use multiple CPU threads → faster
        body.put("options", options);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            log.info("Sending text prompt to Ollama model '{}' (length: {} chars, maxTokens: {})", model, prompt.length(), maxTokens);
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
            ResponseEntity<String> response = getRestTemplate().postForEntity(url, request, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String result = root.has("response") ? root.get("response").asText() : "No response from AI model.";
                log.info("Ollama responded successfully ({} chars)", result.length());
                return result;
            }
            log.error("Ollama returned status: {}", response.getStatusCode());
            return "Error: Received status " + response.getStatusCode();
        } catch (Exception e) {
            log.error("Error communicating with Ollama text model: {}", e.getMessage());
            return "Error communicating with AI model: " + e.getMessage();
        }
    }

    /**
     * Vision generation — sends an image (base64) with a prompt to a vision model (llava).
     * Optimized with speed parameters.
     */
    public String generateWithImage(String prompt, String base64Image) {
        String url = baseUrl + "/api/generate";
        Map<String, Object> body = new HashMap<>();
        body.put("model", visionModel);
        body.put("prompt", prompt);
        body.put("images", List.of(base64Image));
        body.put("stream", false);

        // ── Speed optimizations ──
        Map<String, Object> options = new HashMap<>();
        options.put("num_predict", 300);
        options.put("temperature", 0);
        options.put("top_k", 1);
        options.put("num_ctx", 1024);
        options.put("num_thread", 4);
        body.put("options", options);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            log.info("Sending image prompt to Ollama vision model '{}'", visionModel);
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
            ResponseEntity<String> response = getRestTemplate().postForEntity(url, request, String.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode root = objectMapper.readTree(response.getBody());
                String result = root.has("response") ? root.get("response").asText() : "No response from AI model.";
                log.info("Ollama vision responded successfully ({} chars)", result.length());
                return result;
            }
            log.error("Ollama vision returned status: {}", response.getStatusCode());
            return "Error: Received status " + response.getStatusCode();
        } catch (Exception e) {
            log.error("Error communicating with Ollama vision model: {}", e.getMessage());
            return "Error: Vision model '" + visionModel + "' not available. " + e.getMessage();
        }
    }
}
