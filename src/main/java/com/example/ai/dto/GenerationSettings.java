package com.example.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.Map;

@Data
@Schema(description = "Settings for AI generation behavior")
public class GenerationSettings {
    @Schema(description = "The AI provider (gemini, ollama, vllm, web-service)", example = "gemini")
    private String provider;

    @Schema(description = "Provider-specific configurations (model name, base URL)")
    private Map<String, ProviderConfig> providerConfigs;

    @Schema(description = "Sampling temperature for AI generation (0.0 to 1.0)", example = "0.7")
    private Double temperature;

    @Schema(description = "The language to use for generation (default: Korean)", example = "Korean")
    private String language;

    @Schema(description = "Whether to include descriptive comments in the generated code", example = "true")
    private Boolean includeComments;

    @Schema(description = "Base package for Java code generation", example = "com.example.app")
    private String basePackage;

    @Schema(description = "The target DBMS for SQL generation", example = "oracle")
    private String dbms;

    @Schema(description = "Whether to enable MCP (Model Context Protocol) tools")
    private Boolean mcpEnabled;

    @Schema(description = "Specific MCP server names to use for this request")
    private java.util.List<String> mcpServers;

    @Data
    @Schema(description = "Provider-specific configuration details")
    public static class ProviderConfig {
        @Schema(description = "The name of the AI model to use", example = "gemini-1.5-flash")
        private String modelName;

        @Schema(description = "The base URL for the provider if it's not the default")
        private String baseUrl;
    }
}
