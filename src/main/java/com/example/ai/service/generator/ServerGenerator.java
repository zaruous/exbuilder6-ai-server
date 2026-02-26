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
    
    @Override
    public boolean supports(String stage) {
        return "server".equalsIgnoreCase(stage);
    }

    @Override
    public void generate(GenerateRequest request, GenerationResult.GenerationResultBuilder builder) {
        AiProperties.ServerProperties serverProps = aiProperties.getServer();
        String basePkg = serverProps.getBasePackage();
        String provider = aiProperties.getProvider();

        // 현재 설정된 AI 클라이언트 찾기
        AiClient client = aiClients.stream()
                .filter(c -> c.supports(provider))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Unsupported AI provider: " + provider));

        List<JavaFile> files = new ArrayList<>();
        
        // AI 클라이언트를 호출하여 코드 생성
        String generatedLogic = client.generateContent(request);

        log.info("Generating server files for base package: {}", basePkg);
        files.add(createJavaFile("ItemController.java", basePkg + "." + serverProps.getPackageMapping().get("controller"), "controller", generatedLogic));
        files.add(createJavaFile("ItemService.java", basePkg + "." + serverProps.getPackageMapping().get("service"), "service", generatedLogic));
        files.add(createJavaFile("ItemModel.java", basePkg + "." + serverProps.getPackageMapping().get("model"), "model", generatedLogic));
        
        builder.javaFiles(files)
                .explanation("Spring Boot server components created using " + provider);
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
        file.setContent(content);
        return file;
    }
}
