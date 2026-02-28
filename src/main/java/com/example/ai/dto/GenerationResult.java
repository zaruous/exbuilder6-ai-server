package com.example.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Singular;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Result of the generation process, containing various code snippets and logs.")
public class GenerationResult {
    @Schema(description = "eXbuilder6 Layout XML (clx) generated or provided as context")
    private String clxCode;

    @Schema(description = "eXbuilder6 Script JavaScript generated or provided as context")
    private String jsCode;

    @Schema(description = "SQL code (create, insert, query) generated or provided as context")
    private String sqlCode;

    @Singular
    @Schema(description = "List of Java files (Controller, Service, VO, etc.) generated or provided as context")
    private List<JavaFile> javaFiles;

    @Singular
    @Schema(description = "Logs of the generation process")
    private List<String> logs;

    @Schema(description = "The AI's explanation of the generated code")
    private String explanation;

    @Schema(description = "Mock data or preview script generated for layout preview")
    private String previewMock;
}
