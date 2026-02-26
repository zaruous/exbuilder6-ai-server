package com.example.ai.dto;

import lombok.Data;
import java.util.List;

@Data
public class JavaFile {
    private String fileName;
    private String packagePath;
    private String content;
    private String type; // controller, service, model
}
