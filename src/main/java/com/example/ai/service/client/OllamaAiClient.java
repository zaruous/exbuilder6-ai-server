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
        
        String model = null;
        if (settings != null && settings.getProviderConfigs() != null) {
            // 1. 만약 프론트엔드가 'web-service' 모드라면, 해당 모델명을 최우선으로 함
            if ("web-service".equals(settings.getProvider()) && settings.getProviderConfigs().containsKey("web-service")) {
                model = settings.getProviderConfigs().get("web-service").getModelName();
            }
            
            // 2. 위에서 모델을 못 찾았고 'ollama' 설정이 있으면 사용
            if ((model == null || model.isEmpty()) && settings.getProviderConfigs().containsKey("ollama")) {
                model = settings.getProviderConfigs().get("ollama").getModelName();
            }
        }
        
        // 3. 최종적으로 없으면 시스템 설정값 사용
        if (model == null || model.isEmpty()) {
            model = defaultConfig.getModel();
        }

        Double temperature = (settings != null && settings.getTemperature() != null) 
                        ? settings.getTemperature() : 0.1;
        
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
        if (request.getSystemPrompt() != null) {
            requestBody.put("system", request.getSystemPrompt());
        }
        requestBody.put("stream", false);
        
        Map<String, Object> options = new HashMap<>();
        options.put("temperature", temperature);
        requestBody.put("options", options);

        // MCP 설정 적용
        boolean mcpEnabled = (settings != null && settings.getMcpEnabled() != null)
                ? settings.getMcpEnabled() : aiProperties.getMcp().isEnabled();

        if (mcpEnabled && aiProperties.getMcp().getServers() != null) {
            List<Map<String, Object>> tools = new java.util.ArrayList<>();
            
            // 사용할 서버 이름 결정: 1. 요청 설정 -> 2. 단계별 설정 -> 3. 전체 서버
            List<String> targetServerNames = null;
            if (settings != null && settings.getMcpServers() != null) {
                targetServerNames = settings.getMcpServers();
            } else if (aiProperties.getMcp().getStageServers() != null && request.getStage() != null) {
                targetServerNames = aiProperties.getMcp().getStageServers().get(request.getStage().toLowerCase());
            }

            for (AiProperties.McpServerConfig server : aiProperties.getMcp().getServers()) {
                if (targetServerNames != null && !targetServerNames.contains(server.getName())) {
                    continue;
                }
                Map<String, Object> tool = new HashMap<>();
                tool.put("type", "mcp");
                Map<String, Object> function = new HashMap<>();
                function.put("name", server.getName());
                function.put("description", "MCP Server: " + server.getUrl());
                tool.put("function", function);
                tools.add(tool);
            }
            if (!tools.isEmpty()) {
                requestBody.put("tools", tools);
            }
        }

        try {
            Map<String, Object> response = restTemplate.postForObject(url, requestBody, Map.class);
            return (response != null && response.containsKey("response")) ? (String) response.get("response") : "// No response";
        } catch (Exception e) {
            log.error("Failed to call Ollama API: {}", e.getMessage());
            return "// Error calling Ollama: " + e.getMessage();
        }
    }

    /**
     * Ollama 서버의 버전을 확인합니다.
     */
    public String getVersion() {
        AiProperties.ProviderConfig config = aiProperties.getProviders().get("ollama");
        String url = config.getApiUrl() + "/api/version";
        try {
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            return (response != null && response.containsKey("version")) ? (String) response.get("version") : "unknown";
        } catch (Exception e) {
            log.error("Failed to get Ollama version: {}", e.getMessage());
            return "error: " + e.getMessage();
        }
    }
}
