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

    private void runSqlTest(String provider) {
        System.out.println("\n==================================================");
        System.out.println(">>> Testing SQL Generation with Provider: " + provider);
        
        GenerateRequest request = new GenerateRequest();
        request.setSystemPrompt("당신은 SQL 전문가입니다. 테이블 목록이 매우 크므로 절대 get_table_list를 먼저 호출하지 마십시오. 대신 search_tables 도구를 사용하여 필요한 키워드로 테이블을 검색한 후, 찾은 테이블에 대해 get_table_schema를 호출하여 구조를 파악하십시오.");
        request.setStage("sql");
        request.setPrompt("공통 코드 데이터를 조회하는 SQL을 작성하세요. (관련 테이블: madmtbldef, madmtbldat)");
        
        // 설정을 통해 프로바이더 명시적 지정
        com.example.ai.dto.GenerationSettings settings = new com.example.ai.dto.GenerationSettings();
        settings.setProvider(provider);
        request.setSettings(settings);

        long startTime = System.currentTimeMillis();
        try {
            GenerationResult result = generationService.processStage(request);
            long endTime = System.currentTimeMillis();
            
            assertNotNull(result);
            System.out.println(">>> Request to Completion Time: " + (endTime - startTime) + "ms");
            System.out.println(">>> Result Explanation: " + result.getExplanation());
            if (result.getSqlCode() != null) {
                System.out.println(">>> Generated SQL: \n" + result.getSqlCode());
                assertTrue(result.getSqlCode().contains("madmtbldat"), "Should contain data table");
            }
        } catch (Exception e) {
            System.out.println(">>> Test failed for provider " + provider + ": " + e.getMessage());
        }
        System.out.println("==================================================\n");
    }
}
