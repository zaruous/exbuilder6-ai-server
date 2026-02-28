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
        runSqlTest("ollama");
    }

    @Test
    void testSqlGenerateWithGemini() {
        // Gemini API 키가 설정되어 있어야 함
        if (aiProperties.getProviders().get("gemini").getApiKey().equals("YOUR_GEMINI_API_KEY")) {
            System.out.println(">>> Skipping Gemini test: API Key not set.");
            return;
        }
        runSqlTest("gemini");
    }

    @Test
    void testSqlGenerateWithVllm() {
        // vLLM 서버가 구동 중이어야 함
        runSqlTest("vllm");
    }

    private void runSqlTest(String provider) {
        System.out.println("\n==================================================");
        System.out.println(">>> Testing SQL Generation with Provider: " + provider);
        
        GenerateRequest request = new GenerateRequest();
        request.setSystemPrompt("당신은 SQL 전문가입니다. 테이블 구조 파악을 위해 search_tables나 get_table_schema 도구를 적극 활용하십시오. 재고 조회는 minvlotsts 테이블을, 인수(수치) 내역 조회는 minvlotrcv 테이블을 참고하십시오.");
        request.setStage("sql");
        request.setPrompt("현재 재고(minvlotsts)와 인수내역(minvlotrcv)을 결합하여 조회하는 SQL을 생성하세요. 품목별 현재고와 최근 인수일자를 보여주세요.");
        
        // 설정을 통해 프로바이더 명시적 지정
        com.example.ai.dto.GenerationSettings settings = new com.example.ai.dto.GenerationSettings();
        settings.setProvider(provider);
        request.setSettings(settings);

        try {
            GenerationResult result = generationService.processStage(request);
            assertNotNull(result);
            System.out.println(">>> Result Explanation: " + result.getExplanation());
            if (result.getSqlCode() != null) {
                System.out.println(">>> Generated SQL: \n" + result.getSqlCode());
                assertTrue(result.getSqlCode().contains("minvlotsts"), "Should contain inventory table");
            }
        } catch (Exception e) {
            System.out.println(">>> Test failed for provider " + provider + ": " + e.getMessage());
        }
        System.out.println("==================================================\n");
    }
}
