package com.example.ai.dto;

import lombok.Data;
import java.util.Map;

@Data
public class GenerationSettings {
    private String provider;
    private Map<String, ProviderConfig> providerConfigs;
    private Double temperature;
    private String language;
    private Boolean includeComments;
    private String basePackage;
    private String dbms;

    // MCP 설정
    private Boolean mcpEnabled;
    private java.util.List<String> mcpServers;

    @Data
    public static class ProviderConfig {
        private String modelName;
        private String baseUrl;
    }
}
