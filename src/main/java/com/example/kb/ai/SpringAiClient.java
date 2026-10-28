package com.example.kb.ai;

import com.example.kb.config.KbProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.openai.OpenAiEmbeddingModel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 真实实现：Embedding 交给 Spring AI 的 OpenAiEmbeddingModel，对话交给 {@link ChatEngine}。
 *
 * <p>这个类是「把 Spring AI 适配进已有业务接口」的薄适配层。
 * 好处是业务代码（文档流水线、检索器）完全不用改，
 * 想换模型（OpenAI / 千问 / DeepSeek / 本地 bge）只改配置。
 */
@Slf4j
public class SpringAiClient implements AiClient {

    private final OpenAiEmbeddingModel embeddingModel;
    private final ChatEngine chatEngine;
    private final KbProperties properties;

    public SpringAiClient(OpenAiEmbeddingModel embeddingModel,
                          ChatEngine chatEngine,
                          KbProperties properties) {
        this.embeddingModel = embeddingModel;
        this.chatEngine = chatEngine;
        this.properties = properties;
    }

    @Override
    public int dimension() {
        return properties.getAi().getEmbeddingDim();
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            return new float[dimension()];
        }
        return embeddingModel.embed(text);
    }

    /**
     * 批量向量化。Spring AI 内部会按 token 数自动分批，
     * 比手写「16 条一批 + 重试」更稳，失败重试由框架的 RetryTemplate 负责。
     */
    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> inputs = texts.stream().map(t -> t == null ? "" : t).toList();
        List<float[]> vectors = embeddingModel.embed(inputs);
        if (vectors.size() == inputs.size()) {
            return vectors;
        }
        // 某些批次切分策略下，框架返回的向量条数可能和入参不一致。
        // 一旦错位，落库时就会出现「切片 6 条、向量 1 条」的越界。
        // 这里退化成逐条计算：慢一点，但保证一一对应。
        log.warn("批量向量化返回 {} 条，与入参 {} 条不一致，退化为逐条计算", vectors.size(), inputs.size());
        List<float[]> safe = new ArrayList<>(inputs.size());
        for (String input : inputs) {
            safe.add(embeddingModel.embed(input));
        }
        return safe;
    }

    @Override
    public void chatStream(String prompt, Consumer<String> onDelta) {
        // adhoc：不绑定会话记忆，给非对话场景（工具、摘要）兜底
        chatEngine.stream(null, "adhoc", "", prompt, onDelta);
    }
}
