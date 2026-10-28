package com.example.kb;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

/**
 * 单体 AI 知识库问答系统启动类。
 *
 * <p>排除 {@link UserDetailsServiceAutoConfiguration}：鉴权完全由 JwtAuthenticationFilter
 * 负责，不需要 Spring Security 自动生成的默认内存用户（否则启动日志会多一行随机密码）。
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@MapperScan("com.example.kb.mapper")
public class KbPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(KbPlatformApplication.class, args);
    }
}
