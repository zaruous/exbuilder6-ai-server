package com.example.ai.service.generator;

import com.example.ai.config.AiProperties;
import com.example.ai.dto.GenerateRequest;
import com.example.ai.dto.GenerationResult;
import com.example.ai.service.client.AiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 서비스를 통해 eXbuilder6 화면 제어를 위한 JavaScript 코드를 생성하는 생성기입니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScriptGenerator implements StageGenerator {

    private final AiProperties aiProperties;
    private final List<AiClient> aiClients;
    private final AiResponseParser aiResponseParser;
    
    @Override
    public boolean supports(String stage) {
        return "script".equalsIgnoreCase(stage);
    }

    @Override
    public void generate(GenerateRequest request, GenerationResult.GenerationResultBuilder builder) {
        String provider = aiProperties.getProvider();
        log.info("Generating Script JS using provider: {}", provider);

        AiClient client = aiClients.stream()
                .filter(c -> c.supports(provider))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Unsupported AI provider: " + provider));

        String content = client.generateContent(request);
        GenerationResult parsed = aiResponseParser.parse(content);

        if (parsed != null) {
            if (parsed.getJsCode() != null) builder.jsCode(parsed.getJsCode());
            if (parsed.getExplanation() != null) builder.explanation(parsed.getExplanation());
            if (parsed.getLogs() != null) {
                parsed.getLogs().forEach(builder::log);
            }
        } else {
            log.info("Falling back to raw JavaScript Code: {}", content);
            builder.jsCode(content)
                    .explanation("Controller JavaScript logic generated via " + provider);
        }
    }
}
