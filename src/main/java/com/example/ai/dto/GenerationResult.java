package com.example.ai.dto;

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
public class GenerationResult {
    private String clxCode;
    private String jsCode;
    private String sqlCode;
    @Singular
    private List<JavaFile> javaFiles;
    @Singular
    private List<String> logs;
    private String explanation;
    private String previewMock;
}
