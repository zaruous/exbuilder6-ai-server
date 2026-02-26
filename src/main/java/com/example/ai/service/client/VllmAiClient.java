package com.example.ai.service.client;

import com.example.ai.config.AiProperties;
import com.example.ai.dto.GenerateRequest;
import com.example.ai.dto.GenerationSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * vLLM 서비스와 실제 HTTP 통신을 수행하는 클라이언트입니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VllmAiClient implements AiClient {

    private final AiProperties aiProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public boolean supports(String provider) {
        return "vllm".equalsIgnoreCase(provider);
    }

    @Override
    public String generateContent(GenerateRequest request) {
        AiProperties.ProviderConfig defaultConfig = aiProperties.getProviders().get("vllm");
        GenerationSettings settings = request.getSettings();

        String model = (settings != null && settings.getProviderConfigs() != null && settings.getProviderConfigs().containsKey("vllm")) 
                        ? settings.getProviderConfigs().get("vllm").getModelName() 
                        : defaultConfig.getModel();
        
        String url = defaultConfig.getApiUrl() + "/v1/completions";
        log.info("Sending request to vLLM [Model: {}]", model);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("prompt", request.getPrompt());
        requestBody.put("max_tokens", 2048);
        requestBody.put("stream", false);
        
        if (settings != null && settings.getTemperature() != null) {
            requestBody.put("temperature", settings.getTemperature());
        }

        try {
            Map<String, Object> response = restTemplate.postForObject(url, requestBody, Map.class);
            if (response != null && response.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                return (String) choices.get(0).get("text");
            }
            return "// No response";
        } catch (Exception e) {
            log.error("Failed to call vLLM API: {}", e.getMessage());
            return "// Error calling vLLM: " + e.getMessage();
        }
    }
}
