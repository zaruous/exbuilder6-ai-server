package com.example.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "Request for testing prompt with context code snippets")
public class TestPromptRequest {
    @Schema(description = "The prompt/request to the AI", example = "위 SQL을 기반으로 자바 서비스 코드를 작성해줘.")
    private String prompt;

    @Schema(description = "SQL code to include in context")
    private String sql;

    @Schema(description = "eXbuilder6 Layout XML (clx) to include in context")
    private String clx;

    @Schema(description = "eXbuilder6 JavaScript code to include in context")
    private String js;

    @Schema(description = "Java files (Service, Controller, etc.) to include in context")
    private List<JavaFile> javaFiles;

    @Schema(description = "The target stage for generation", example = "server")
    private String stage;

    @Schema(description = "Additional settings for AI generation")
    private GenerationSettings settings;
}
