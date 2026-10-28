package com.example.kb.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 接口文档：启动后访问 http://localhost:8080/doc.html
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI kbOpenApi() {
        return new OpenAPI().info(new Info()
                .title("AI 知识库问答平台 API")
                .version("1.0.0")
                .description("单体项目接口文档：登录、知识库、文档、问答"));
    }
}
