package com.example.kb.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 大模型能力的抽象。业务层只依赖这个接口，
 * 想从「自己写的 RestClient」换成 Spring AI 的 ChatClient，只改实现类即可。
 *
 * @see OpenAiCompatibleAiClient 默认实现，兼容通义千问 / DeepSeek / Kimi 等
 */
public interface AiClient {

    /** 向量维度，必须和 pgvector 建表时的 vector(N) 一致。 */
    int dimension();

    /** 把一段文本转成向量。 */
    float[] embed(String text);

    /**
     * 批量向量化。
     * 默认逐条调用，真实实现应覆盖成本方法走批量接口并自带重试。
     */
    default List<float[]> embedBatch(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(embed(text));
        }
        return vectors;
    }

    /** 流式对话：模型每吐出一小段就回调一次 onDelta。 */
    void chatStream(String prompt, Consumer<String> onDelta);

    /** 非流式对话，给不方便用 SSE 的场景兜底。 */
    default String chat(String prompt) {
        StringBuilder sb = new StringBuilder();
        chatStream(prompt, sb::append);
        return sb.toString();
    }
}
