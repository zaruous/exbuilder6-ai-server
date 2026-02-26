package com.example.ai.service.generator;

import com.example.ai.dto.GenerateRequest;
import com.example.ai.dto.GenerationResult;

/**
 * eXbuilder6 AI 생성을 위한 단계별 생성기 인터페이스입니다.
 * SQL, 서버 코드, 레이아웃 XML, 스크립트 등 각 단계별 생성 로직을 확장성 있게 구현하기 위해 사용됩니다.
 */
public interface StageGenerator {
    
    /**
     * 해당 생성기가 지정된 단계를 지원하는지 여부를 확인합니다.
     * 
     * @param stage 검사할 단계명 (예: sql, server, layout, script)
     * @return 지원 가능 여부
     */
    boolean supports(String stage);
    
    /**
     * 요청 정보를 바탕으로 코드를 생성하고 결과 빌더에 설정합니다.
     * 
     * @param request 생성 요청 정보 (프롬프트, 설정 등 포함)
     * @param builder 결과 데이터를 담을 GenerationResult 빌더 객체
     */
    void generate(GenerateRequest request, GenerationResult.GenerationResultBuilder builder);
}
