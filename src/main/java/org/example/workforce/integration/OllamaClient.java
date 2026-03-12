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
    @Value("${ollama.timeout:120000}")
    private int timeout;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RestTemplate getRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofMillis(10000));
        factory.setReadTimeout(java.time.Duration.ofMillis(timeout));
        return new RestTemplate(factory);
    }

    /** Check if Ollama is reachable */
    public boolean isAvailable() {
        try {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(java.time.Duration.ofMillis(3000));
            factory.setReadTimeout(java.time.Duration.ofMillis(3000));
            RestTemplate quickTemplate = new RestTemplate(factory);
            ResponseEntity<String> response = quickTemplate.getForEntity(baseUrl + "/api/tags", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Ollama is not reachable at {}: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    /** Text-only generation (phi3) */
    public String generate(String prompt) {
        String url = baseUrl + "/api/generate";
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("stream", false);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try {
            log.info("Sending text prompt to Ollama model '{}' (length: {} chars)", model, prompt.length());
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
     */
    public String generateWithImage(String prompt, String base64Image) {
        String url = baseUrl + "/api/generate";
        Map<String, Object> body = new HashMap<>();
        body.put("model", visionModel);
        body.put("prompt", prompt);
        body.put("images", List.of(base64Image));
        body.put("stream", false);
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
