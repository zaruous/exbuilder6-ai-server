package com.example.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.util.List;

@Data
@Schema(description = "Structure of a generated Java file")
public class JavaFile {
    @Schema(description = "The name of the Java file (e.g., UserService.java)")
    private String fileName;

    @Schema(description = "The package path (e.g., com.example.ai.service)")
    private String packagePath;

    @Schema(description = "The content of the Java file")
    private String content;

    @Schema(description = "Type of the Java file (e.g., controller, service, model)")
    private String type;
}
