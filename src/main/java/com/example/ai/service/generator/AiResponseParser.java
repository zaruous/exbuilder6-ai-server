package com.example.ai.service.generator;

import com.example.ai.dto.GenerationResult;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class AiResponseParser {

    private final ObjectMapper objectMapper;

    public AiResponseParser() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public GenerationResult parse(String content) {
        if (content == null || content.trim().isEmpty()) {
            return new GenerationResult();
        }

        String jsonContent = extractJson(content);
        try {
            return objectMapper.readValue(jsonContent, GenerationResult.class);
        } catch (Exception e) {
            log.warn("Failed to parse AI response as JSON: {}. Falling back to raw content.", e.getMessage());
            return null;
        }
    }

    private String extractJson(String content) {
        // Markdown JSON 블록 제거 (```json ... ``` 또는 ``` ...)
        Pattern pattern = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return content.trim();
    }
}
