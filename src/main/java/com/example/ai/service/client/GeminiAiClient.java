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
 * Google Gemini API와 실제 HTTP 통신을 수행하는 클라이언트입니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiAiClient implements AiClient {

    private final AiProperties aiProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public boolean supports(String provider) {
        return "gemini".equalsIgnoreCase(provider);
    }

    @Override
    public String generateContent(GenerateRequest request) {
        AiProperties.ProviderConfig defaultConfig = aiProperties.getProviders().get("gemini");
        GenerationSettings settings = request.getSettings();
        
        String model = (settings != null && settings.getProviderConfigs() != null && settings.getProviderConfigs().containsKey("gemini")) 
                        ? settings.getProviderConfigs().get("gemini").getModelName() 
                        : defaultConfig.getModel();
        
        String baseUrl = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent";
        String url = baseUrl + "?key=" + defaultConfig.getApiKey();
        
        log.info("Sending request to Gemini API [Model: {}]", model);

        // 컨텍스트 포함 프롬프트 구성
        String prompt = request.getPrompt();
        if (request.getContext() != null && request.getContext().getExplanation() != null) {
            prompt = "Context of previous step: " + request.getContext().getExplanation() + "\nRequest: " + prompt;
        }

        Map<String, Object> textPart = new HashMap<>();
        textPart.put("text", prompt);

        Map<String, Object> contentPart = new HashMap<>();
        contentPart.put("parts", List.of(textPart));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("contents", List.of(contentPart));
        
        // 시스템 프롬프트 추가 (system_instruction)
        if (request.getSystemPrompt() != null) {
            Map<String, Object> systemPart = new HashMap<>();
            systemPart.put("text", request.getSystemPrompt());
            
            Map<String, Object> systemInstruction = new HashMap<>();
            systemInstruction.put("parts", List.of(systemPart));
            
            requestBody.put("system_instruction", systemInstruction);
        }
        
        // 모델 옵션 (temperature 등) 적용
        if (settings != null && settings.getTemperature() != null) {
            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("temperature", settings.getTemperature());
            requestBody.put("generationConfig", generationConfig);
        }

        try {
            Map<String, Object> response = restTemplate.postForObject(url, requestBody, Map.class);
            if (response != null && response.containsKey("candidates")) {
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                if (!candidates.isEmpty()) {
                    Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                    List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                    return (String) parts.get(0).get("text");
                }
            }
            return "// No content generated";
        } catch (Exception e) {
            log.error("Failed to call Gemini API: {}", e.getMessage());
            return "// Error calling Gemini API: " + e.getMessage();
        }
    }
}
