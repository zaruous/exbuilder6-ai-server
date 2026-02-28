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
 * 타임아웃 문제를 해결하기 위해 복잡한 SSE 대신 직접 API를 호출합니다.
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
     * MCP 서버로부터 사용 가능한 도구 목록을 조회합니다.
     * (REST Bridge: /tables 엔드포인트 사용)
     */
    public String listTools(McpServerConfig config) {
        String baseUrl = config.getUrl().replace("/sse", "");
        log.info("Fetching tools via REST Bridge: {}/tables", baseUrl);
        
        try {
            // MCP 서버의 /tables API는 이미 테이블 목록을 반환함
            String tableList = webClient.get()
                    .uri(baseUrl + "/tables")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(java.time.Duration.ofSeconds(5));

            // MCP tools/list 스펙에 맞게 래핑하여 반환
            return "{\"tools\":[" +
                    "{\"name\":\"get_table_list\",\"description\":\"DB 테이블 목록 조회\",\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}," +
                    "{\"name\":\"get_table_schema\",\"description\":\"특정 테이블 스키마 조회\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"tableName\":{\"type\":\"string\"}},\"required\":[\"tableName\"]}}" +
                    "]}";
        } catch (Exception e) {
            log.error("Failed to fetch tools via REST: {}", e.getMessage());
            throw new RuntimeException("MCP REST call failed", e);
        }
    }

    /**
     * MCP 서버에 도구 실행을 요청합니다.
     */
    public String callTool(McpServerConfig config, String toolName, Map<String, Object> arguments) {
        String baseUrl = config.getUrl().replace("/sse", "");
        log.info("Calling tool '{}' via REST Bridge", toolName);

        try {
            if ("get_table_list".equals(toolName)) {
                return webClient.get()
                        .uri(baseUrl + "/tables")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(java.time.Duration.ofSeconds(5));
            } else if ("get_table_schema".equals(toolName)) {
                String tableName = (String) arguments.get("tableName");
                return webClient.get()
                        .uri(baseUrl + "/tables/" + tableName + "/schema")
                        .retrieve()
                        .bodyToMono(String.class)
                        .block(java.time.Duration.ofSeconds(5));
            }
            throw new UnsupportedOperationException("Unsupported tool: " + toolName);
        } catch (Exception e) {
            log.error("Tool execution failed: {}", e.getMessage());
            throw new RuntimeException("MCP Tool call failed", e);
        }
    }
}
