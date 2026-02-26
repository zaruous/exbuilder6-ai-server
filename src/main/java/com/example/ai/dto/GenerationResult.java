package com.example.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerationResult {
    private String clxCode;
    private String jsCode;
    private String sqlCode;
    private List<JavaFile> javaFiles;
    private List<String> logs;
    private String explanation;
    private String previewMock;
}
