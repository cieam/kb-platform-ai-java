package com.example.kb.ai.tool;

import com.example.kb.retrieval.HybridRetriever;
import com.example.kb.vector.ChunkVector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 给大模型用的工具（Spring AI 的 {@code @Tool}）。
 *
 * <p>为什么有了 RAG 还要工具？RAG 是「一次检索 → 一次生成」，
 * 当参考资料不足时，模型只能拒答；有了这个工具，模型可以自己决定「再检索一次」，
 * 这是 Agent 相比固定 RAG 链路的关键差别。
 *
 * <p>安全设计：kbId 不从模型参数里取，而是从 {@link ToolContext} 取。
 * ToolContext 是服务端在调用前塞进去的（见 ChatEngine），模型无法伪造，
 * 因此不存在「诱导模型去查别人的知识库」这类越权问题。
 */
@Slf4j
@Component
public class KnowledgeTools {

    /** 单条摘录注入上下文的最大长度，防止一次工具调用把 Prompt 撑爆 */
    private static final int MAX_EXCERPT_CHARS = 600;

    private final HybridRetriever hybridRetriever;

    public KnowledgeTools(HybridRetriever hybridRetriever) {
        this.hybridRetriever = hybridRetriever;
    }

    @Tool(description = """
            在当前知识库中检索与问题相关的原文片段，返回带编号的摘录。
            当【参考资料】不足以回答用户问题、或需要补充细节时调用它。
            """)
    public String searchKnowledgeBase(
            @ToolParam(description = "要检索的问题或关键词") String query,
            @ToolParam(description = "返回条数，默认 5，最大 10", required = false) Integer topK,
            ToolContext toolContext) {

        Object rawKbId = toolContext == null || toolContext.getContext() == null
                ? null
                : toolContext.getContext().get("kbId");
        if (rawKbId == null) {
            return "当前会话没有绑定知识库，无法检索。";
        }

        long kbId;
        try {
            kbId = Long.parseLong(String.valueOf(rawKbId));
        } catch (NumberFormatException e) {
            return "知识库标识不合法，无法检索。";
        }
        if (kbId <= 0) {
            return "当前会话没有绑定知识库，无法检索。";
        }

        long start = System.currentTimeMillis();
        List<ChunkVector> hits = hybridRetriever.retrieve(kbId, query);
        if (hits.isEmpty()) {
            return "知识库中没有检索到与「" + query + "」相关的内容。";
        }

        int limit = topK == null ? hits.size() : Math.max(1, Math.min(topK, 10));
        StringBuilder sb = new StringBuilder();
        sb.append("检索到 ").append(Math.min(limit, hits.size())).append(" 条相关片段：\n");
        for (int i = 0; i < Math.min(limit, hits.size()); i++) {
            sb.append('[').append(i + 1).append("] ")
                    .append(trim(hits.get(i).getContent()))
                    .append('\n');
        }
        log.debug("工具检索完成 kbId={} query={} hits={} costMs={}",
                kbId, query, hits.size(), System.currentTimeMillis() - start);
        return sb.toString();
    }

    private String trim(String text) {
        if (text == null) {
            return "";
        }
        return text.length() <= MAX_EXCERPT_CHARS ? text : text.substring(0, MAX_EXCERPT_CHARS) + "…";
    }
}
