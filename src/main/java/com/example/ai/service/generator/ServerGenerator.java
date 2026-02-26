package com.example.ai.service.generator;

import com.example.ai.config.AiProperties;
import com.example.ai.dto.GenerateRequest;
import com.example.ai.dto.GenerationResult;
import com.example.ai.dto.JavaFile;
import com.example.ai.service.client.AiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * AI 서비스를 통해 Spring Boot 서버 코드를 생성하는 생성기입니다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ServerGenerator implements StageGenerator {

    private final AiProperties aiProperties;
    private final List<AiClient> aiClients;
    private final AiResponseParser aiResponseParser;
    
    @Override
    public boolean supports(String stage) {
        return "server".equalsIgnoreCase(stage);
    }

    @Override
    public void generate(GenerateRequest request, GenerationResult.GenerationResultBuilder builder) {
        AiProperties.ServerProperties serverProps = aiProperties.getServer();
        String basePkg = serverProps.getBasePackage();
        String provider = aiProperties.getProvider();

        AiClient client = aiClients.stream()
                .filter(c -> c.supports(provider))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Unsupported AI provider: " + provider));

        String content = client.generateContent(request);
        GenerationResult parsed = aiResponseParser.parse(content);

        if (parsed != null && parsed.getJavaFiles() != null && !parsed.getJavaFiles().isEmpty()) {
            builder.javaFiles(parsed.getJavaFiles());
            if (parsed.getExplanation() != null) builder.explanation(parsed.getExplanation());
            if (parsed.getLogs() != null) {
                parsed.getLogs().forEach(builder::log);
            }
        } else {
            log.info("Generating server files via template for provider: {}", provider);
            builder.javaFile(createJavaFile("ItemController.java", basePkg + "." + serverProps.getPackageMapping().get("controller"), "controller", content));
            builder.javaFile(createJavaFile("ItemService.java", basePkg + "." + serverProps.getPackageMapping().get("service"), "service", content));
            builder.javaFile(createJavaFile("ItemModel.java", basePkg + "." + serverProps.getPackageMapping().get("model"), "model", content));
            
            builder.explanation("Spring Boot server components created using " + provider);
        }
    }

    private JavaFile createJavaFile(String fileName, String packagePath, String type, String aiGeneratedContent) {
        JavaFile file = new JavaFile();
        file.setFileName(fileName);
        file.setPackagePath(packagePath);
        file.setType(type);

        String annotation = type.equals("model") ? "Component" : type.substring(0, 1).toUpperCase() + type.substring(1);
        String classAnnotation = type.equals("model") ? "Data" : annotation;

        String content = "package " + packagePath + ";\n\n" +
                         "import org.springframework.stereotype." + annotation + ";\n" +
                         (type.equals("model") ? "import lombok.Data;\n\n" : "\n") +
                         "@" + classAnnotation + "\n" +
                         "public class " + fileName.replace(".java", "") + " {\n" +
                         "    " + aiGeneratedContent + "\n" +
                         "}";
        log.info("Generated content for {}: \n{}", fileName, content);
        file.setContent(content);
        return file;
    }
}
