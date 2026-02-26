package com.example.ai.controller;

import com.example.ai.dto.GenerateRequest;
import com.example.ai.dto.GenerationResult;
import com.example.ai.service.GenerationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Slf4j
public class GenerationController {

    private final GenerationService generationService;

    @PostMapping("/generate")
    public GenerationResult generate(@RequestBody GenerateRequest request) {
        log.info("Received generation request for stage: {}, prompt: {}", request.getStage(), request.getPrompt());
        return generationService.processStage(request);
    }
}
