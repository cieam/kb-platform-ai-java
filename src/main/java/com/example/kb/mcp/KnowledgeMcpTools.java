package com.example.kb.mcp;

import com.example.kb.domain.entity.KnowledgeBase;
import com.example.kb.retrieval.HybridRetriever;
import com.example.kb.service.KnowledgeBaseService;
import com.example.kb.vector.ChunkVector;
import com.example.kb.vector.VectorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通过 MCP 暴露给外部 AI 客户端的工具。
 *
 * <p>和 {@code KnowledgeTools} 的区别：
 * <ul>
 *   <li>KnowledgeTools 是给「本工程的对话模型」用的，kbId 走 ToolContext 隐式传递；</li>
 *   <li>这里的工具是给「外部 Agent / IDE 插件」用的，没有登录态，
 *       所以 kbId 必须显式传入，并且每个参数都要自己校验。</li>
 * </ul>
 *
 * <p>这套工具会被 spring-ai-starter-mcp-server 自动注册到 MCP Server，
 * 客户端先 listTools 拿到 schema，再 callTool 调用，不需要知道 HTTP 细节。
 */
@Slf4j
@Component
public class KnowledgeMcpTools {

    private static final int MAX_EXCERPT_CHARS = 800;

    private final HybridRetriever hybridRetriever;
    private final VectorRepository vectorRepository;
    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeMcpTools(HybridRetriever hybridRetriever,
                             VectorRepository vectorRepository,
                             KnowledgeBaseService knowledgeBaseService) {
        this.hybridRetriever = hybridRetriever;
        this.vectorRepository = vectorRepository;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Tool(description = "在指定知识库中做混合检索（向量 + 关键词），返回相关原文片段。适合让外部 Agent 复用本平台的知识库能力。")
    public String searchKnowledgeBase(
            @ToolParam(description = "知识库 ID") long kbId,
            @ToolParam(description = "自然语言问题或关键词") String query) {

        if (kbId <= 0 || query == null || query.isBlank()) {
            return "参数不合法：kbId 必须大于 0，query 不能为空。";
        }
        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(kbId);
        if (knowledgeBase == null) {
            return "知识库 " + kbId + " 不存在。";
        }

        List<ChunkVector> hits = hybridRetriever.retrieve(kbId, query);
        if (hits.isEmpty()) {
            return "知识库「" + knowledgeBase.getName() + "」中没有检索到相关内容。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("知识库：").append(knowledgeBase.getName()).append('\n');
        for (int i = 0; i < hits.size(); i++) {
            sb.append('[').append(i + 1).append("] ")
                    .append(trim(hits.get(i).getContent()))
                    .append('\n');
        }
        log.debug("MCP 检索 kbId={} query={} hits={}", kbId, query, hits.size());
        return sb.toString();
    }

    @Tool(description = "查询知识库规模：文档数量与已向量化的切片数量，用于判断该知识库是否可用。")
    public String knowledgeBaseStats(@ToolParam(description = "知识库 ID") long kbId) {
        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(kbId);
        if (knowledgeBase == null) {
            return "知识库 " + kbId + " 不存在。";
        }
        int chunkCount = vectorRepository.countByKbId(kbId);
        return "知识库「" + knowledgeBase.getName() + "」：文档 "
                + knowledgeBase.getDocCount() + " 篇，切片 " + chunkCount + " 条。";
    }

    private String trim(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_EXCERPT_CHARS ? text : text.substring(0, MAX_EXCERPT_CHARS) + "…";
    }
}
