package org.example.workforce.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.HashMap;
import java.util.Map;

@Component
public class OllamaClient {
    @Value("${ollama.base-url:http://localhost:11434}")
    private String baseUrl;
    @Value("${ollama.model:phi3}")
    private String model;
    @Value("${ollama.timeout:60000}")
    private int timeout;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public String generate(String prompt){
        String url = baseUrl + "/api/generate";
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        body.put("stream", false);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        try{
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if(response.getStatusCode().is2xxSuccessful() && response.getBody() != null){
                JsonNode root = objectMapper.readTree(response.getBody());
                return root.has("response") ? root.get("response").asText() : "No response from AI model.";
            }
            return "Error: Received status " + response.getStatusCode();
        } catch (Exception e) {
            return "Error communicating with AI model: " + e.getMessage();
        }
    }
}
