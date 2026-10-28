package com.example.kb.ai;

import com.example.kb.config.KbProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 未配置 API Key 时的本地兜底实现，保证工程克隆下来就能跑通完整链路。
 *
 * <p>向量不是真的 Embedding，而是「字符 + 相邻二元组哈希到固定桶再归一化」：
 * 字面重叠的文本余弦相似度确实更高，所以「检索 → 生成 → 引用」这条链路能真实工作，
 * 只是生成内容是占位文本。配好 Key 后会自动切换到 {@link SpringAiClient}。
 */
@Component
public class MockAiClient implements AiClient {

    private final KbProperties properties;

    public MockAiClient(KbProperties properties) {
        this.properties = properties;
    }

    @Override
    public int dimension() {
        return properties.getAi().getEmbeddingDim();
    }

    @Override
    public float[] embed(String text) {
        return localEmbedding(text);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        for (String text : texts) {
            vectors.add(localEmbedding(text));
        }
        return vectors;
    }

    @Override
    public void chatStream(String prompt, Consumer<String> onDelta) {
        List<String> pieces = List.of(
                "【模拟回答】当前未配置大模型 API Key，下面是固定占位内容。\n",
                "检索、融合、引用来源、流式输出都已经真实执行，只是生成结果不是模型产出的。\n",
                "把 kb.ai.api-key 配上（或设置环境变量 KB_AI_API_KEY=你的Key）即可接入真实模型。");
        for (String piece : pieces) {
            onDelta.accept(piece);
            try {
                Thread.sleep(150L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private float[] localEmbedding(String text) {
        int dim = dimension();
        float[] vector = new float[dim];
        String s = text == null ? "" : text;
        for (int i = 0; i < s.length(); i++) {
            int h1 = Math.abs(s.charAt(i) * 31 + i + 1) % dim;
            vector[h1] += 1.0f;
            if (i + 1 < s.length()) {
                int h2 = Math.abs(s.charAt(i) * 131 + s.charAt(i + 1) * 7) % dim;
                vector[h2] += 0.5f;
            }
        }
        double norm = 0;
        for (float v : vector) {
            norm += (double) v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dim; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }
        return vector;
    }
}
