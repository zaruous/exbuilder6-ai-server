package com.example.ai.service.client;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import com.example.ai.config.AiProperties.McpServerConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * MCP 서버와 직접 REST 통신을 수행하는 서비스입니다.
 * 타임아웃 문제를 해결하기 위해 복잡한 SSE(Server-Sent Events) 프로토콜을 건너뛰고 
 * 직접 REST API를 호출(Direct REST Bridge)하여 성능과 안정성을 극대화합니다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class McpService {

    private final ObjectMapper objectMapper;
    private final WebClient webClient = WebClient.builder()
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(10 * 1024 * 1024))
            .build();

    /**
     * MCP 서버로부터 사용 가능한 도구 목록을 반환합니다.
     * 설정 파일(application.yml)에 등록된 도구 목록을 즉시 반환하여 SSE 핸드셰이크 지연을 방지합니다.
     */
    public String listTools(McpServerConfig config) {
        log.info("Returning tools specification for MCP server '{}' from configuration", config.getName());
        
        try {
            if (config.getTools() == null || config.getTools().isEmpty()) {
                log.warn("No tools defined for MCP server: {}", config.getName());
                return "{\"tools\":[]}";
            }
            return objectMapper.writeValueAsString(Map.of("tools", config.getTools()));
        } catch (Exception e) {
            log.error("Failed to serialize tools for {}: {}", config.getName(), e.getMessage());
            return "{\"tools\":[], \"error\":\"" + e.getMessage() + "\"}";
        }
    }

    /**
     * MCP 서버에 REST API를 통해 도구 실행을 직접 요청합니다.
     */
    public String callTool(McpServerConfig config, String toolName, Map<String, Object> arguments) {
        String baseUrl = getBaseRestUrl(config.getUrl());
        log.info("Calling tool '{}' via REST Bridge at {}", toolName, baseUrl);

        try {
            switch (toolName) {
                case "get_table_list":
                    return webClient.get()
                            .uri(baseUrl + "/tables")
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(java.time.Duration.ofSeconds(10));
                            
                case "search_tables":
                    String query = (String) arguments.get("query");
                    return webClient.get()
                            .uri(baseUrl + "/tables/search?q={query}", query)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(java.time.Duration.ofSeconds(10));
                            
                case "get_table_schema":
                    String tableName = (String) arguments.get("tableName");
                    return webClient.get()
                            .uri(baseUrl + "/tables/" + tableName + "/schema")
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(java.time.Duration.ofSeconds(10));
                            
                case "read_query":
                case "write_query":
                case "explain_query":
                    String endpoint = toolName.replace("_query", ""); // read, write, explain
                    String sql = (String) arguments.get("sql");
                    return webClient.post()
                            .uri(baseUrl + "/query/" + endpoint)
                            .contentType(MediaType.TEXT_PLAIN)
                            .bodyValue(sql)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(java.time.Duration.ofSeconds(15));
                            
                default:
                    throw new UnsupportedOperationException("Unsupported tool requested: " + toolName);
            }
        } catch (Exception e) {
            log.error("Tool execution failed ({}): {}", toolName, e.getMessage());
            return "{\"error\":\"Tool execution failed: " + e.getMessage() + "\"}";
        }
    }

    /**
     * SSE URL에서 REST API 호출을 위한 Base URL을 추출합니다.
     */
    private String getBaseRestUrl(String sseUrl) {
        String baseUrl = sseUrl.replace("/sse", "").replace("?sessionId=", "");
        if (baseUrl.contains("/messages")) {
            baseUrl = baseUrl.substring(0, baseUrl.indexOf("/messages"));
        }
        // 트레일링 슬래시 제거
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        return baseUrl;
    }
}
