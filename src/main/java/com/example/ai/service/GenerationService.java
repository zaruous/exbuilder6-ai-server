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
        GenerationResult context = request.getContext();
        
        GenerationResult.GenerationResultBuilder builder = GenerationResult.builder();

        // 이전 컨텍스트 데이터 필드값만 유지 (로그와 자바파일은 프론트엔드에서 머지하므로 제외하거나 필요시에만 포함)
        if (context != null) {
            builder.sqlCode(context.getSqlCode())
                   .clxCode(context.getClxCode())
                   .jsCode(context.getJsCode())
                   .previewMock(context.getPreviewMock())
                   .explanation(context.getExplanation());
        }

        builder.log("Spring Backend: Processing " + stage + " for '" + prompt + "'");

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
