package com.example.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * AI 생성 엔진 및 연동 서비스의 설정을 관리하는 프로퍼티 클래스입니다.
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.generation")
public class AiProperties {
    
    /**
     * 활성화할 생성 단계 목록
     */
    private List<String> activeStages;

    /**
     * 사용할 AI 제공자 타입 (gemini, ollama, vllm 등)
     */
    private String provider = "gemini";

    /**
     * 각 제공자별 상세 설정
     */
    private Map<String, ProviderConfig> providers;

    /**
     * 서버 코드 생성 관련 상세 옵션
     */
    private ServerProperties server = new ServerProperties();

    @Data
    public static class ProviderConfig {
        private String apiUrl;
        private String apiKey;
        private String model;
    }

    @Data
    public static class ServerProperties {
        private String basePackage = "com.example.generated";
        private Map<String, String> packageMapping = Map.of(
            "controller", "controller",
            "service", "service",
            "model", "model"
        );
    }
}
