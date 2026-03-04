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

    /**
     * 공통 코드 데이터 검색
     */
    @Test
    void testRagSqlGenerate() {
        System.out.println("\n==================================================");
        System.out.println(">>> Testing RAG (Vector DB Knowledge) SQL Generation");
        
        GenerateRequest request = new GenerateRequest();
        // 시스템 프롬프트: 지식 베이스 활용을 강력히 권고
        request.setSystemPrompt("당신은 데이터베이스 전문가입니다. " +
            "사용자의 질문에 답하기 위해 어떤 테이블이 필요한지 모를 때는 반드시 'search_knowledge_base' 도구를 사용하여 관련 정보를 검색하십시오. " +
            "검색 결과를 바탕으로 가장 적합한 테이블을 찾아 SQL을 작성하고, 어떤 지식을 참고했는지 설명에 포함하세요."
            +  " factory_code가 컬럼에 존재하는 경우 값으로 1100으로 넣으시오. ");
        
        request.setStage("sql");
        // 테이블명을 숨기고 "자연어 비즈니스 질문"만 던짐
        request.setPrompt("우리 시스템에서 '공통 코드'를 관리하고 그 상세 데이터를 저장하는 테이블들이 무엇인지 지식 베이스에서 찾아서, 코드가 'SYSTEM'인 데이터를 조회하는 SQL을 작성하세요.");
        
        
        com.example.ai.dto.GenerationSettings settings = new com.example.ai.dto.GenerationSettings();
        settings.setProvider("ollama"); // 현재 기본 설정인 ollama 사용
        request.setSettings(settings);

        long startTime = System.currentTimeMillis();
        try {
            GenerationResult result = generationService.processStage(request);
            long endTime = System.currentTimeMillis();
            
            assertNotNull(result);
            System.out.println(">>> Request to Completion Time: " + (endTime - startTime) + "ms");
            System.out.println(">>> RAG Search Result & Explanation: \n" + result.getExplanation());
            
            if (result.getSqlCode() != null) {
                System.out.println(">>> Generated SQL based on Knowledge: \n" + result.getSqlCode());
                // 결과에 예상되는 테이블명(madmtbldef 등)이 포함되었는지 확인
                assertTrue(result.getSqlCode().toLowerCase().contains("madmtbl"), "Should find tables related to common codes");
            }
        } catch (Exception e) {
            System.out.println(">>> RAG Test failed: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("==================================================\n");
    }
    
    /**
     * 사용자 테이블과 로그인 이력 테이블 검
     */
    @Test
    void testRagSqlGenerate2() {
        System.out.println("\n==================================================");
        System.out.println(">>> Testing RAG (Vector DB Knowledge) SQL Generation");
        
        GenerateRequest request = new GenerateRequest();
        // 시스템 프롬프트: 지식 베이스 활용을 강력히 권고
        request.setSystemPrompt("당신은 데이터베이스 전문가입니다. " +
            "사용자의 질문에 답하기 위해 어떤 테이블이 필요한지 모를 때는 반드시 'search_knowledge_base' 도구를 사용하여 관련 정보를 검색하십시오. " +
            "검색 결과를 바탕으로 가장 적합한 테이블을 찾아 SQL을 작성하고, 어떤 지식을 참고했는지 설명에 포함하세요."
            +  " factory_code가 컬럼에 존재하는 경우 값으로 1100으로 넣으시오. ");
        
        request.setStage("sql");
        
        // 사용자 테이블과 로그인 이력 조회
        request.setPrompt("우리 시스템에서 '사용자 테이블'과 '로그인 이력'을 관리하는 테이블들이 무엇인지 지식 베이스에서 찾아서, 사용자 ID가 123인 사용자의 로그인 이력을 조회하는 SQL을 작성하세요.'");
        
        
        com.example.ai.dto.GenerationSettings settings = new com.example.ai.dto.GenerationSettings();
        settings.setProvider("ollama"); // 현재 기본 설정인 ollama 사용
        request.setSettings(settings);

        long startTime = System.currentTimeMillis();
        try {
            GenerationResult result = generationService.processStage(request);
            long endTime = System.currentTimeMillis();
            
            assertNotNull(result);
            System.out.println(">>> Request to Completion Time: " + (endTime - startTime) + "ms");
            System.out.println(">>> RAG Search Result & Explanation: \n" + result.getExplanation());
            
            if (result.getSqlCode() != null) {
                System.out.println(">>> Generated SQL based on Knowledge: \n" + result.getSqlCode());
                // 결과에 예상되는 테이블명(madmtbldef 등)이 포함되었는지 확인
                assertTrue(result.getSqlCode().toLowerCase().contains("madm"), "Should find tables related to common codes");
            }
        } catch (Exception e) {
            System.out.println(">>> RAG Test failed: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("==================================================\n");
    }
    
    @Test
    void testRagSqlGenerate3() {
        System.out.println("\n==================================================");
        System.out.println(">>> Testing RAG (Vector DB Knowledge) SQL Generation");
        
        GenerateRequest request = new GenerateRequest();
        // 시스템 프롬프트: 지식 베이스 활용을 강력히 권고
        request.setSystemPrompt("당신은 데이터베이스 전문가입니다. " +
            "사용자의 질문에 답하기 위해 어떤 테이블이 필요한지 모를 때는 반드시 'search_knowledge_base' 도구를 사용하여 관련 정보를 검색하십시오. " +
            "검색 결과를 바탕으로 가장 적합한 테이블을 찾아 SQL을 작성하고, 어떤 지식을 참고했는지 설명에 포함하세요."
            +  " factory_code가 컬럼에 존재하는 경우 값으로 1100으로 넣으시오. ");
        
        request.setStage("sql");
        
        // 사용자 테이블과 로그인 이력 조회
        request.setPrompt("우리 시스템에서 '재고'와 '인수 내역' 테이블을 통해 재고가 어떻게 인수되었는지 정보를 조회하는 SQL을 작성하세요.'");
        
        
        com.example.ai.dto.GenerationSettings settings = new com.example.ai.dto.GenerationSettings();
        settings.setProvider("ollama"); // 현재 기본 설정인 ollama 사용
        request.setSettings(settings);

        long startTime = System.currentTimeMillis();
        try {
            GenerationResult result = generationService.processStage(request);
            long endTime = System.currentTimeMillis();
            
            assertNotNull(result);
            System.out.println(">>> Request to Completion Time: " + (endTime - startTime) + "ms");
            System.out.println(">>> RAG Search Result & Explanation: \n" + result.getExplanation());
            
            if (result.getSqlCode() != null) {
                System.out.println(">>> Generated SQL based on Knowledge: \n" + result.getSqlCode());
                // 결과에 예상되는 테이블명(madmtbldef 등)이 포함되었는지 확인
                assertTrue(result.getSqlCode().toLowerCase().contains("inv"), "Should find tables related to common codes");
            }
        } catch (Exception e) {
            System.out.println(">>> RAG Test failed: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println("==================================================\n");
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
