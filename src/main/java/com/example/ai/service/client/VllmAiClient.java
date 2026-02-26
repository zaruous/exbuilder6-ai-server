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

        String model = null;
        if (settings != null && settings.getProviderConfigs() != null) {
            if ("web-service".equals(settings.getProvider()) && settings.getProviderConfigs().containsKey("web-service")) {
                model = settings.getProviderConfigs().get("web-service").getModelName();
            }
            if ((model == null || model.isEmpty()) && settings.getProviderConfigs().containsKey("vllm")) {
                model = settings.getProviderConfigs().get("vllm").getModelName();
            }
        }
        
        if (model == null || model.isEmpty()) {
            model = defaultConfig.getModel();
        }
        
        String url = defaultConfig.getApiUrl() + "/v1/completions";
        log.info("Sending request to vLLM [Model: {}]", model);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", model);
        requestBody.put("prompt", request.getPrompt());
        if (request.getSystemPrompt() != null) {
            // vLLM completions API에서는 프롬프트 앞에 결합하거나 별도 파라미터로 처리 (vLLM 버전에 따라 다를 수 있음)
            // 여기서는 프롬프트 앞에 지시사항으로 추가하는 방식 적용
            requestBody.put("prompt", "System: " + request.getSystemPrompt() + "\n\nUser: " + request.getPrompt());
        }
        requestBody.put("max_tokens", 2048);
        requestBody.put("stream", false);
        
        if (settings != null && settings.getTemperature() != null) {
            requestBody.put("temperature", settings.getTemperature());
        }

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
                tool.put("type", "function");
                Map<String, Object> function = new HashMap<>();
                function.put("name", "mcp_" + server.getName());
                function.put("parameters", Map.of("url", server.getUrl()));
                tool.put("function", function);
                tools.add(tool);
            }
            if (!tools.isEmpty()) {
                requestBody.put("tools", tools);
            }
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
