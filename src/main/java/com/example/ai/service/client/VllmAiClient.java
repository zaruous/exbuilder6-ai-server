package com.example.ai.service.client;

import com.example.ai.config.AiProperties;
import com.example.ai.dto.GenerateRequest;
import com.example.ai.dto.GenerationSettings;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * vLLM 서비스와 연동하며, OpenAI 호환 API를 통해 MCP 도구 호출을 지원하는 에이전트 클라이언트입니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class VllmAiClient implements AiClient {

    private final AiProperties aiProperties;
    private final McpService mcpService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_ITERATIONS = 5;

    private final Map<String, AiProperties.McpServerConfig> toolToServerMap = new HashMap<>();

    @Override
    public boolean supports(String provider) {
        return "vllm".equalsIgnoreCase(provider);
    }

    @Override
    public String generateContent(GenerateRequest request) {
        AiProperties.ProviderConfig defaultConfig = aiProperties.getProviders().get("vllm");
        GenerationSettings settings = request.getSettings();

        String model = resolveModel(settings, defaultConfig);
        String url = defaultConfig.getApiUrl() + "/v1/chat/completions";
        
        log.info("Starting Agent Loop with vLLM [Model: {}]", model);

        List<Map<String, Object>> messages = new ArrayList<>();
        if (request.getSystemPrompt() != null) {
            messages.add(Map.of("role", "system", "content", request.getSystemPrompt()));
        }
        messages.add(Map.of("role", "user", "content", buildFinalPrompt(request)));

        List<Map<String, Object>> tools = prepareTools(request, settings);

        for (int i = 1; i <= MAX_ITERATIONS; i++) {
            log.info("vLLM Agent iteration {}/{}", i, MAX_ITERATIONS);
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("stream", false);
            if (!tools.isEmpty()) {
                requestBody.put("tools", tools);
            }

            try {
                Map<String, Object> response = restTemplate.postForObject(url, requestBody, Map.class);
                if (response == null || !response.containsKey("choices")) return "// Error: Invalid vLLM response";

                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> assistantMessage = (Map<String, Object>) choice.get("message");
                messages.add(assistantMessage);

                List<Map<String, Object>> toolCalls = (List<Map<String, Object>>) assistantMessage.get("tool_calls");
                if (toolCalls == null || toolCalls.isEmpty()) {
                    return (String) assistantMessage.get("content");
                }

                for (Map<String, Object> toolCall : toolCalls) {
                    Map<String, Object> function = (Map<String, Object>) toolCall.get("function");
                    String toolName = (String) function.get("name");
                    
                    // vLLM/OpenAI 호환 API에서 arguments는 종종 String 형태로 전달됨
                    Object rawArgs = function.get("arguments");
                    Map<String, Object> arguments;
                    if (rawArgs instanceof String) {
                        arguments = objectMapper.readValue((String) rawArgs, new TypeReference<Map<String, Object>>() {});
                    } else {
                        arguments = (Map<String, Object>) rawArgs;
                    }

                    String toolCallId = (String) toolCall.get("id");
                    log.info("vLLM requesting tool: {}", toolName);
                    
                    String toolResult = executeMcpTool(toolName, arguments);
                    
                    Map<String, Object> toolMessage = new HashMap<>();
                    toolMessage.put("role", "tool");
                    toolMessage.put("tool_call_id", toolCallId);
                    toolMessage.put("name", toolName);
                    toolMessage.put("content", toolResult);
                    messages.add(toolMessage);
                }
            } catch (Exception e) {
                log.error("vLLM agent loop error: {}", e.getMessage(), e);
                return "// Agent Error: " + e.getMessage();
            }
        }

        return "// Max iterations reached";
    }

    private String executeMcpTool(String toolName, Map<String, Object> arguments) {
        AiProperties.McpServerConfig config = toolToServerMap.get(toolName);
        if (config == null) return "Error: Tool server not found";

        try {
            String responseJson = mcpService.callTool(config, toolName, arguments);
            try {
                Map<String, Object> result = objectMapper.readValue(responseJson, new TypeReference<Map<String, Object>>() {});
                if (result.containsKey("content")) {
                    List<Map<String, Object>> contentList = (List<Map<String, Object>>) result.get("content");
                    StringBuilder sb = new StringBuilder();
                    for (Map<String, Object> item : contentList) {
                        if ("text".equals(item.get("type"))) sb.append(item.get("text"));
                    }
                    return sb.toString();
                }
            } catch (Exception ignored) {}
            return responseJson;
        } catch (Exception e) {
            return "Execution Error: " + e.getMessage();
        }
    }

    private List<Map<String, Object>> prepareTools(GenerateRequest request, GenerationSettings settings) {
        List<Map<String, Object>> tools = new ArrayList<>();
        if (aiProperties.getMcp().getServers() == null) return tools;
        
        toolToServerMap.clear();
        for (AiProperties.McpServerConfig s : aiProperties.getMcp().getServers()) {
            try {
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
                log.warn("Failed to load tools from {}: {}", s.getName(), e.getMessage());
            }
        }
        return tools;
    }

    private String resolveModel(GenerationSettings settings, AiProperties.ProviderConfig defaultConfig) {
        if (settings != null && settings.getProviderConfigs() != null && settings.getProviderConfigs().containsKey("vllm")) {
            return settings.getProviderConfigs().get("vllm").getModelName();
        }
        return defaultConfig.getModel();
    }

    private String buildFinalPrompt(GenerateRequest request) {
        StringBuilder sb = new StringBuilder();
        if (request.getContext() != null) {
            sb.append("Previous Context:\n").append(request.getContext().getExplanation()).append("\n\n");
        }
        sb.append("Request: ").append(request.getPrompt());
        return sb.toString();
    }
}
