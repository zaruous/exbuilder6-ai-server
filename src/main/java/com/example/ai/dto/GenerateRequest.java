package com.example.ai.dto;

import lombok.Data;

@Data
public class GenerateRequest {
    private String prompt;
    private String stage; // sql, server, layout, script
    private GenerationResult context;
    private GenerationSettings settings;
}
