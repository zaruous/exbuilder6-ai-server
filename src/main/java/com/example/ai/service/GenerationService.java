package com.example.ai.service;

import com.example.ai.dto.GenerateRequest;
import com.example.ai.dto.GenerationResult;
import com.example.ai.service.generator.StageGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GenerationService {

    private final List<StageGenerator> generators;
    private final com.example.ai.config.AiProperties aiProperties;

    public GenerationResult processStage(GenerateRequest request) {
        String stage = request.getStage();
        log.info("Processing stage: {}", stage);
        
        // YAML에 설정된 활성 단계인지 확인
        if (aiProperties.getActiveStages() != null && !aiProperties.getActiveStages().contains(stage)) {
            return GenerationResult.builder()
                    .explanation("Stage '" + stage + "' is disabled in configuration.")
                    .build();
        }
        
        String prompt = request.getPrompt();
        
        List<String> logs = new ArrayList<>();
        logs.add("Spring Backend: Processing " + stage + " for '" + prompt + "'");
        
        GenerationResult.GenerationResultBuilder builder = GenerationResult.builder()
                .logs(logs);

        generators.stream()
                .filter(g -> g.supports(stage))
                .findFirst()
                .ifPresentOrElse(
                        g -> g.generate(request, builder),
                        () -> builder.explanation("Unknown stage: " + stage)
                );

        return builder.build();
    }
}
