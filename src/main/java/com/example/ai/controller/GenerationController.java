package com.example.ai.controller;

import com.example.ai.dto.GenerateRequest;
import com.example.ai.dto.GenerationResult;
import com.example.ai.dto.TestPromptRequest;
import com.example.ai.service.GenerationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Generation", description = "AI code generation endpoints")
public class GenerationController {

    private final GenerationService generationService;

    @PostMapping("/generate")
    @Operation(summary = "Generate code for a specific stage", description = "Generates code (SQL, Layout, Script, or Server) based on the provided prompt and context.")
    public GenerationResult generate(@RequestBody GenerateRequest request) {
        log.info("Received generation request for stage: {}, prompt: {}", request.getStage(), request.getPrompt());
        return generationService.processStage(request);
    }

    @PostMapping("/test-context")
    @Operation(summary = "Test prompt with specific context", description = "Allows providing SQL, CLX, JS, and Java code as context to test AI generation for a given prompt.")
    public GenerationResult testContext(@RequestBody TestPromptRequest testRequest) {
        log.info("Received test-context request for stage: {}, prompt: {}", testRequest.getStage(), testRequest.getPrompt());
        
        // Convert TestPromptRequest to GenerateRequest
        GenerateRequest request = new GenerateRequest();
        request.setPrompt(testRequest.getPrompt());
        request.setStage(testRequest.getStage());
        request.setSettings(testRequest.getSettings());
        
        GenerationResult context = GenerationResult.builder()
                .sqlCode(testRequest.getSql())
                .clxCode(testRequest.getClx())
                .jsCode(testRequest.getJs())
                .javaFiles(testRequest.getJavaFiles())
                .build();
        request.setContext(context);
        
        return generationService.processStage(request);
    }
}
