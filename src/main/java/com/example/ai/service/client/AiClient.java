package com.example.ai.service.client;

import com.example.ai.dto.GenerateRequest;

/**
 * 외부 AI 서비스(Gemini, Ollama 등)와 통신하기 위한 클라이언트 인터페이스입니다.
 */
public interface AiClient {
    /**
     * 해당 클라이언트가 지원하는 제공자 타입인지 확인합니다.
     */
    boolean supports(String provider);

    /**
     * AI 모델에 요청을 전달하고 생성된 텍스트를 반환받습니다.
     * 
     * @param request 생성 요청 정보 (프롬프트, 단계, 히스토리/컨텍스트, 설정 포함)
     * @return 생성된 코드 또는 텍스트
     */
    String generateContent(GenerateRequest request);
}
