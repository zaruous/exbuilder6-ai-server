package com.example.ai.service.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.example.ai.config.AiProperties;
import com.example.ai.dto.GenerateRequest;
import com.example.ai.dto.GenerationSettings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 로컬 Ollama 서비스와 연동하며, MCP SSE 프로토콜의 세션 핸들링을 지원하는 에이전트 클라이언트입니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OllamaAiClient implements AiClient {

    private final AiProperties aiProperties;
    private final McpService mcpService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_ITERATIONS = 15;

    // 도구 이름과 해당 도구를 제공하는 MCP 서버 설정 간의 매핑을 저장
    private final Map<String, AiProperties.McpServerConfig> toolToServerMap = new HashMap<>();

    @Override
    public boolean supports(String provider) {
        return "ollama".equalsIgnoreCase(provider);
    }

    @Override
    public String generateContent(GenerateRequest request) {
        AiProperties.ProviderConfig defaultConfig = aiProperties.getProviders().get("ollama");
        GenerationSettings settings = request.getSettings();
        
        String model = resolveModel(settings, defaultConfig);
        Double temperature = (settings != null && settings.getTemperature() != null) ? settings.getTemperature() : 0.1;
        
        String url = defaultConfig.getApiUrl() + "/api/chat";
        log.info("Starting Agent Loop with Ollama [Model: {}]", model);

        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null) {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", buildFinalPrompt(request)));

        boolean mcpEnabled = isMcpEnabled(settings);
        List<Map<String, Object>> tools = prepareTools(request, settings);

        for (int i = 1; i <= MAX_ITERATIONS; i++) {
            log.info("Agent iteration {}/{}", i, MAX_ITERATIONS);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("stream", false);
            
            if (mcpEnabled && !tools.isEmpty()) {
                requestBody.put("tools", tools);
            }
            requestBody.put("options", Map.of("temperature", temperature));

            try {
                Map<String, Object> response = restTemplate.postForObject(url, requestBody, Map.class);
                if (response == null || !response.containsKey("message")) return "// Error: Invalid response";

                Map<String, Object> assistantMessage = (Map<String, Object>) response.get("message");
                messages.add(assistantMessage);

                List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) assistantMessage.get("tool_calls");
                if (toolCalls == null || toolCalls.isEmpty()) {
                    return (String) assistantMessage.get("content");
                }

                for (Map<String, Object> toolCall : toolCalls) {
                    Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                    String toolName = (String) function.get("name");
                    Map<String, Object> arguments = (Map<String, Object>) function.get("arguments");
                    String toolCallId = (String) toolCall.get("id");

                    log.info("Agent requesting tool execution: {} (ID: {})", toolName, toolCallId);
                    String toolResult = executeMcpTool(toolName, arguments);
                    
                    Map<String, Object> toolMessage = new HashMap<>();
                    toolMessage.put("role", "tool");
                    toolMessage.put("content", toolResult);
                    if (toolCallId != null) {
                        toolMessage.put("tool_call_id", toolCallId);
                    }
                    messages.add(toolMessage);
                }
            } catch (Exception e) {
            	e.printStackTrace();
                log.error("Agent loop exception: {}", e.getMessage());
                return "// Agent Error: " + e.getMessage();
            }
        }
        return "// Max iterations reached";
    }

    private String executeMcpTool(String toolName, Map<String, Object> arguments) {
        AiProperties.McpServerConfig config = toolToServerMap.get(toolName);
        if (config == null) return "Error: MCP Server for tool '" + toolName + "' not found";

        try {
            return mcpService.callTool(config, toolName, arguments);
        } catch (Exception e) {
            log.error("MCP Execution failed for tool {}: {}", toolName, e.getMessage());
            return "Execution Error: " + e.getMessage();
        }
    }

    private List<Map<String, Object>> prepareTools(GenerateRequest request, GenerationSettings settings) {
        List<Map<String, Object>> tools = new ArrayList<>();
        if (!isMcpEnabled(settings) || aiProperties.getMcp().getServers() == null) return tools;
        
        toolToServerMap.clear();

        for (AiProperties.McpServerConfig s : aiProperties.getMcp().getServers()) {
            try {
                log.info("Fetching tools from MCP server: {}", s.getName());
                String response = mcpService.listTools(s);
                
                Map<String, Object> result = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
                List<Map<String, Object>> mcpTools = (List<Map<String, Object>>) result.get("tools");
                
                if (mcpTools != null) {
                    for (Map<String, Object> t : mcpTools) {
                        String name = (String) t.get("name");
                        toolToServerMap.put(name, s);
                        
                        tools.add(Map.of(
                            "type", "function",
                            "function", Map.of(
                                "name", name,
                                "description", t.getOrDefault("description", ""),
                                "parameters", t.getOrDefault("inputSchema", Map.of("type", "object", "properties", Map.of()))
                            )
                        ));
                    }
                }
            } catch (Exception e) {
            	e.printStackTrace();
                log.warn("Failed to fetch tools from MCP server {}: {}", s.getName(), e.getMessage());
            }
        }
        return tools;
    }

    private String resolveModel(GenerationSettings settings, AiProperties.ProviderConfig defaultConfig) {
        if (settings != null && settings.getProviderConfigs() != null) {
            String m = null;
            if ("web-service".equals(settings.getProvider())) m = settings.getProviderConfigs().get("web-service").getModelName();
            if (m == null || m.isEmpty()) m = settings.getProviderConfigs().get("ollama").getModelName();
            if (m != null && !m.isEmpty()) return m;
        }
        return defaultConfig.getModel();
    }

    private String buildFinalPrompt(GenerateRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.getContext() != null) {
            if (request.getContext().getSqlCode() != null) sb.append("SQL Context:\n").append(request.getContext().getSqlCode()).append("\n\n");
        }
        sb.append("Request: ").append(request.getPrompt());
        return sb.toString();
    }

    private boolean isMcpEnabled(GenerationSettings settings) {
        return (settings != null && settings.getMcpEnabled() != null) 
                ? settings.getMcpEnabled() : aiProperties.getMcp().isEnabled();
    }

    public String getVersion() {
        try {
            Map r = restTemplate.getForObject(aiProperties.getProviders().get("ollama").getApiUrl() + "/api/version", Map.class);
            return r != null ? r.get("version").toString() : "unknown";
        } catch (Exception e) { return "error"; }
    }
}
