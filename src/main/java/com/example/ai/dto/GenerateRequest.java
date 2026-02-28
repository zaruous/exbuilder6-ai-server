package com.example.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "Request for code generation based on a prompt and context")
public class GenerateRequest {
    @Schema(description = "The prompt/request to the AI", example = "위 SQL을 바탕으로 자바 서비스 코드를 작성해줘.")
    private String prompt;

    @Schema(description = "Optional system instruction for the AI")
    private String systemPrompt;

    @Schema(description = "Target stage (sql, layout, script, server, preview-mock)", example = "server")
    private String stage;

    @Schema(description = "The context from previous steps (SQL, Layout, Script, etc.)")
    private GenerationResult context;

    @Schema(description = "Additional AI generation settings")
    private GenerationSettings settings;
}
