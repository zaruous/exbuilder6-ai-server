package com.example.ai.service;

import com.example.ai.dto.GenerateRequest;
import com.example.ai.dto.GenerationResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 실제 빈들을 로드하여 AI 서비스 호출 흐름을 테스트합니다.
 * (주의: 실제 Ollama 서비스가 실행 중이어야 결과가 정상 출력됩니다.)
 */
@SpringBootTest
public class GenerationIntegrationTest {

    @Autowired
    private GenerationService generationService;

    @Test
    void testRealOllamaCall() {
        // Given
        GenerateRequest request = new GenerateRequest();
        request.setStage("sql");
        request.setPrompt("Create a user table for oracle");

        // When
        System.out.println(">>> Starting Real AI Call...");
        GenerationResult result = generationService.processStage(request);

        // Then
        System.out.println(">>> AI Result Explanation: " + result.getExplanation());
        System.out.println(">>> Generated Code:\n" + result.getSqlCode());
        
        assertNotNull(result);
    }
}
