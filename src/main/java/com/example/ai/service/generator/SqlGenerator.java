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
 * AI 서비스를 통해 데이터베이스 SQL 코드를 생성하는 생성기입니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SqlGenerator implements StageGenerator {

    private final AiProperties aiProperties;
    private final List<AiClient> aiClients;

    @Override
    public boolean supports(String stage) {
        return "sql".equalsIgnoreCase(stage);
    }

    @Override
    public void generate(GenerateRequest request, GenerationResult.GenerationResultBuilder builder) {
        String provider = aiProperties.getProvider();
        log.info("Generating SQL using provider: {}", provider);
        AiClient client = aiClients.stream()
                .filter(c -> c.supports(provider))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Unsupported AI provider: " + provider));

        String sqlCode = client.generateContent(request);
        
        builder.sqlCode(sqlCode)
                .explanation("SQL generated via " + provider);
    }
}
