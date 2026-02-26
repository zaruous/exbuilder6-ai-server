package com.example.ai.service;

import com.example.ai.config.AiProperties;
import com.example.ai.dto.GenerateRequest;
import com.example.ai.dto.GenerationResult;
import com.example.ai.service.client.OllamaAiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mockito 대신 실제 Spring Context와 application.yml 설정을 로드하여 테스트합니다.
 */
@SpringBootTest
public class GenerationServiceTest {

    @Autowired
    private GenerationService generationService;
    
    @Autowired
    private AiProperties aiProperties;

    @Autowired
    private OllamaAiClient ollamaAiClient;

    @Test
    void testOllamaVersion() {
        System.out.println("\n==================================================");
        System.out.println(">>> Checking Ollama Version...");
        String version = ollamaAiClient.getVersion();
        System.out.println(">>> Ollama Server Version: " + version);
        System.out.println("==================================================\n");
        
        assertNotNull(version);
        assertFalse(version.startsWith("error"), "Ollama server should be reachable. Error: " + version);
    }

    @Test
    void testPropertiesLoading() {
        assertNotNull(aiProperties.getProvider());
        assertNotNull(aiProperties.getActiveStages());
        System.out.println(">>> Loaded Provider from YML: " + aiProperties.getProvider());
    }

    @Test
    void testSqlGenerate() {
        GenerateRequest request = new GenerateRequest();
        request.setSystemPrompt("당신은 SQL 전문가입니다. 결과는 SQL만 출력해야 합니다.");
        request.setStage("sql");
        request.setPrompt("품질 검사 결과를 조회하는  SQL 생성");

        GenerationResult result = generationService.processStage(request);

        assertNotNull(result);
        System.out.println(">>> Test Result with Real Config: " + result.getExplanation());
        
        if (result.getSqlCode() != null) {
            System.out.println(">>> Generated SQL: \n" + result.getSqlCode());
        }
    }
}
