package com.example.ai.service.client;

import com.example.ai.config.AiProperties;
import com.example.ai.dto.GenerateRequest;
import com.example.ai.dto.GenerationSettings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.Map;
import java.util.HashMap;

/**
 * 로컬 Ollama 서비스와 실제 HTTP 통신을 수행하는 클라이언트입니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OllamaAiClient implements AiClient {

    private final AiProperties aiProperties;
    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public boolean supports(String provider) {
        return "ollama".equalsIgnoreCase(provider);
    }

    @Override
    public String generateContent(GenerateRequest request) {
        AiProperties.ProviderConfig defaultConfig = aiProperties.getProviders().get("ollama");
        GenerationSettings settings = request.getSettings();
        
        // 요청 설정이 있으면 우선 적용, 없으면 시스템 설정값 사용
        String model = (settings != null && settings.getProviderConfigs() != null && settings.getProviderConfigs().containsKey("ollama")) 
                        ? settings.getProviderConfigs().get("ollama").getModelName() 
                        : defaultConfig.getModel();
        Double temperature = (settings != null && settings.getTemperature() != null) 
                        ? settings.getTemperature() : 0.7;
        
        String url = defaultConfig.getApiUrl() + "/api/generate";
        
        log.info("Sending request to Ollama: {} [Model: {}, Temp: {}]", url, model, temperature);

        // 프롬프트 구성 시 히스토리(context)를 참고하여 컨텍스트 강화 가능
        String finalPrompt = request.getPrompt();
        if (request.getContext() != null) {
            finalPrompt = "Previous context: " + request.getContext().getExplanation() + "\n\n" + finalPrompt;
        }

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("prompt", finalPrompt);
        requestBody.put("stream", false);
        
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", temperature);
        requestBody.put("options", options);

        try {
            Map<String, Object> response = restTemplate.postForObject(url, requestBody, Map.class);
            return (response != null && response.containsKey("response")) ? (String) response.get("response") : "// No response";
        } catch (Exception e) {
            throw new RuntimeException("Failed to communicate with Ollama service: " + e.getMessage(), e);
        }
    }
}
