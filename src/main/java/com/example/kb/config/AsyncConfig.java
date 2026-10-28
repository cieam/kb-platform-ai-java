package com.example.kb.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 文档解析与大模型调用都跑在这个线程池里，和 Tomcat 的工作线程隔离，
 * 否则一个 200 页的 PDF 就能把 Web 线程占满，整个服务卡死。
 */
@Configuration
public class AsyncConfig {

    public static final String AI_EXECUTOR = "aiExecutor";

    @Bean(AI_EXECUTOR)
    public ThreadPoolTaskExecutor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(200);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("kb-ai-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
