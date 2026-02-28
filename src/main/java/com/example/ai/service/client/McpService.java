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
     * MCP 서버로부터 사용 가능한 도구 목록을 반환합니다. (Hardcoded API Specification)
     * SSE 통신을 배제하여 타임아웃을 방지합니다.
     */
    public String listTools(McpServerConfig config) {
        log.info("Returning tools specification via REST Bridge (Bypassing SSE Handshake)");
        
        // MCP tools/list 스펙에 맞게 하드코딩하여 즉시 반환 (속도 극대화)
        return "{\"tools\":[" +
                "{\"name\":\"get_table_list\",\"description\":\"DB 테이블 목록을 100개까지 조회합니다.\",\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}," +
                "{\"name\":\"search_tables\",\"description\":\"테이블 이름에 포함된 키워드로 검색합니다.\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}},\"required\":[\"query\"]}}," +
                "{\"name\":\"get_table_schema\",\"description\":\"특정 테이블의 상세 스키마(컬럼 정보)를 조회합니다.\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"tableName\":{\"type\":\"string\"}},\"required\":[\"tableName\"]}}," +
                "{\"name\":\"read_query\",\"description\":\"SELECT SQL 쿼리를 실행하여 데이터를 확인합니다.\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"sql\":{\"type\":\"string\"}},\"required\":[\"sql\"]}}," +
                "{\"name\":\"write_query\",\"description\":\"CUD(Insert/Update/Delete) SQL 쿼리를 실행합니다.\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"sql\":{\"type\":\"string\"}},\"required\":[\"sql\"]}}," +
                "{\"name\":\"explain_query\",\"description\":\"SQL의 실행 계획(Execution Plan)을 분석합니다.\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"sql\":{\"type\":\"string\"}},\"required\":[\"sql\"]}}" +
                "]}";
    }

    /**
     * MCP 서버에 REST API를 통해 도구 실행을 직접 요청합니다.
     */
    public String callTool(McpServerConfig config, String toolName, Map<String, Object> arguments) {
        // 기존 sse endpoint 에서 /sse 를 제거하여 base URL 획득 (예: http://localhost:7070)
        String baseUrl = config.getUrl().replace("/sse", "").replace("?sessionId=", "");
        if (baseUrl.contains("/messages")) {
            baseUrl = baseUrl.substring(0, baseUrl.indexOf("/messages"));
        }
        final String finalBaseUrl = baseUrl;
        
        log.info("Calling tool '{}' via REST Bridge at {}", toolName, finalBaseUrl);

        try {
            switch (toolName) {
                case "get_table_list":
                    return webClient.get()
                            .uri(finalBaseUrl + "/tables")
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(java.time.Duration.ofSeconds(10));
                            
                case "search_tables":
                    String query = (String) arguments.get("query");
                    return webClient.get()
                            .uri(finalBaseUrl + "/tables/search?q={query}", query)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(java.time.Duration.ofSeconds(10));
                            
                case "get_table_schema":
                    String tableName = (String) arguments.get("tableName");
                    return webClient.get()
                            .uri(finalBaseUrl + "/tables/" + tableName + "/schema")
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(java.time.Duration.ofSeconds(10));
                            
                case "read_query":
                    String readSql = (String) arguments.get("sql");
                    return webClient.post()
                            .uri(finalBaseUrl + "/query/read")
                            .contentType(MediaType.TEXT_PLAIN)
                            .bodyValue(readSql)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(java.time.Duration.ofSeconds(15));
                            
                case "write_query":
                    String writeSql = (String) arguments.get("sql");
                    return webClient.post()
                            .uri(finalBaseUrl + "/query/write")
                            .contentType(MediaType.TEXT_PLAIN)
                            .bodyValue(writeSql)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(java.time.Duration.ofSeconds(15));
                            
                case "explain_query":
                    String explainSql = (String) arguments.get("sql");
                    return webClient.post()
                            .uri(finalBaseUrl + "/query/explain")
                            .contentType(MediaType.TEXT_PLAIN)
                            .bodyValue(explainSql)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block(java.time.Duration.ofSeconds(10));
                            
                default:
                    throw new UnsupportedOperationException("Unsupported tool requested: " + toolName);
            }
        } catch (Exception e) {
            log.error("Tool execution failed: {}", e.getMessage());
            return "{\"error\":\"Tool execution failed: " + e.getMessage() + "\"}";
        }
    }
}
