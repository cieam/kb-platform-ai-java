package com.example.kb.mcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP Server 装配。
 *
 * <p>spring-ai-starter-mcp-server-webmvc 的自动配置会扫描容器里的
 * {@link ToolCallbackProvider}，把里面的工具注册成 MCP 能力，
 * 并按 {@code spring.ai.mcp.server.protocol} 决定传输方式：
 * <ul>
 *   <li>SSE —— 走 HTTP，本工程默认，端点 /mcp/sse，适合 Web / IDE 插件；</li>
 *   <li>STDIO —— 走标准输入输出，适合桌面客户端本地拉起进程，
 *       把 protocol 改成 STDIO 并去掉 web 依赖即可。</li>
 * </ul>
 *
 * <p>这就是「一套检索能力，多个 AI 客户端复用」的实现位置。
 */
@Configuration
public class McpServerConfig {

    @Bean
    public ToolCallbackProvider kbToolCallbackProvider(KnowledgeMcpTools knowledgeMcpTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(knowledgeMcpTools)
                .build();
    }
}
