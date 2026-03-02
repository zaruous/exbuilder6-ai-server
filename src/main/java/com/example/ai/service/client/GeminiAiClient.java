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
 * Google Gemini API와 연동하며, MCP 도구 호출을 지원하는 에이전트 클라이언트입니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiAiClient implements AiClient {

    private final AiProperties aiProperties;
    private final McpService mcpService;
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MAX_ITERATIONS = 5;

    // 도구 이름과 해당 도구를 제공하는 MCP 서버 설정 간의 매핑을 저장
    private final Map<String, AiProperties.McpServerConfig> toolToServerMap = new HashMap<>();

    @Override
    public boolean supports(String provider) {
        return "gemini".equalsIgnoreCase(provider);
    }

    @Override
    public String generateContent(GenerateRequest request) {
        AiProperties.ProviderConfig defaultConfig = aiProperties.getProviders().get("gemini");
        GenerationSettings settings = request.getSettings();
        
        String model = resolveModel(settings, defaultConfig);
        String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + defaultConfig.getApiKey();
        
        log.info("Starting Agent Loop with Gemini [Model: {}]", model);

        List<Map<String, Object>> contents = new ArrayList<>();
        
        // 1. 초기 사용자 프롬프트 구성
        Map<String, Object> userContent = new HashMap<>();
        userContent.put("role", "user");
        userContent.put("parts", List.of(Map.of("text", buildFinalPrompt(request))));
        contents.add(userContent);

        Map<String, Object> requestBody = new HashMap<>();
        
        // 시스템 프롬프트 (system_instruction)
        if (request.getSystemPrompt() != null) {
            requestBody.put("system_instruction", Map.of("parts", List.of(Map.of("text", request.getSystemPrompt()))));
        }
        
        // 생성 설정 (temperature 등)
        if (settings != null && settings.getTemperature() != null) {
            requestBody.put("generationConfig", Map.of("temperature", settings.getTemperature()));
        }

        // 도구 준비
        List<Map<String, Object>> tools = prepareTools(request, settings);
        if (!tools.isEmpty()) {
            requestBody.put("tools", List.of(Map.of("function_declarations", tools)));
        }

        for (int i = 1; i <= MAX_ITERATIONS; i++) {
            log.info("Gemini Agent iteration {}/{}", i, MAX_ITERATIONS);
            requestBody.put("contents", contents);

            try {
                Map<String, Object> response = restTemplate.postForObject(url, requestBody, Map.class);
                if (response == null || !response.containsKey("candidates")) return "// Error: Invalid Gemini response";

                List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                if (candidates.isEmpty()) return "// Error: No candidates in response";

                Map<String, Object> candidate = candidates.get(0);
                Map<String, Object> assistantContent = (Map<String, Object>) candidate.get("content");
                contents.add(assistantContent);

                List<Map<String, Object>> parts = (List<Map<String, Object>>) assistantContent.get("parts");
                List<Map<String, Object>> toolCalls = new ArrayList<>();
                String textResponse = null;

                for (Map<String, Object> part : parts) {
                    if (part.containsKey("functionCall")) {
                        toolCalls.add((Map<String, Object>) part.get("functionCall"));
                    } else if (part.containsKey("text")) {
                        textResponse = (String) part.get("text");
                    }
                }

                if (toolCalls.isEmpty()) {
                    return textResponse != null ? textResponse : "// Error: Empty part response";
                }

                // 도구 실행 처리
                List<Map<String, Object>> toolResponseParts = new ArrayList<>();
                for (Map<String, Object> toolCall : toolCalls) {
                    String toolName = (String) toolCall.get("name");
                    Map<String, Object> arguments = (Map<String, Object>) toolCall.get("args");

                    log.info("Gemini requesting tool: {}", toolName);
                    String toolResult = executeMcpTool(toolName, arguments);
                    
                    Map<String, Object> toolResponsePart = new HashMap<>();
                    toolResponsePart.put("functionResponse", Map.of(
                        "name", toolName,
                        "response", Map.of("content", toolResult)
                    ));
                    toolResponseParts.add(toolResponsePart);
                }

                Map<String, Object> toolResponseContent = new HashMap<>();
                toolResponseContent.put("role", "function");
                toolResponseContent.put("parts", toolResponseParts);
                contents.add(toolResponseContent);

            } catch (Exception e) {
                log.error("Gemini agent loop error: {}", e.getMessage(), e);
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
            // REST Bridge 응답(JSON)에서 텍스트 추출 시도
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
        List<Map<String, Object>> toolDeclarations = new ArrayList<>();
        if (aiProperties.getMcp() == null || aiProperties.getMcp().getServers() == null) return toolDeclarations;
        
        // MCP 활성화 여부 확인
        boolean mcpEnabled = (settings != null && settings.getMcpEnabled() != null) 
                            ? settings.getMcpEnabled() 
                            : aiProperties.getMcp().isEnabled();
        if (!mcpEnabled) return toolDeclarations;

        List<String> targetServers = (settings != null) ? settings.getMcpServers() : null;
        toolToServerMap.clear();

        for (AiProperties.McpServerConfig s : aiProperties.getMcp().getServers()) {
            // 요청에 특정 서버 목록이 지정된 경우 필터링
            if (targetServers != null && !targetServers.isEmpty() && !targetServers.contains(s.getName())) {
                continue;
            }

            try {
                String response = mcpService.listTools(s);
                Map<String, Object> result = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
                List<Map<String, Object>> mcpTools = (List<Map<String, Object>>) result.get("tools");
                
                if (mcpTools != null) {
                    for (Map<String, Object> t : mcpTools) {
                        String name = (String) t.get("name");
                        toolToServerMap.put(name, s);
                        
                        Map<String, Object> decl = new HashMap<>();
                        decl.put("name", name);
                        decl.put("description", t.getOrDefault("description", ""));
                        
                        // Gemini는 inputSchema의 'properties'를 'parameters'로 사용
                        Map<String, Object> mcpSchema = (Map<String, Object>) t.get("inputSchema");
                        if (mcpSchema != null) {
                            decl.put("parameters", mcpSchema);
                        }
                        
                        toolDeclarations.add(decl);
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to load tools from {}: {}", s.getName(), e.getMessage());
            }
        }
        return toolDeclarations;
    }

    private String resolveModel(GenerationSettings settings, AiProperties.ProviderConfig defaultConfig) {
        if (settings != null && settings.getProviderConfigs() != null && settings.getProviderConfigs().containsKey("gemini")) {
            return settings.getProviderConfigs().get("gemini").getModelName();
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
