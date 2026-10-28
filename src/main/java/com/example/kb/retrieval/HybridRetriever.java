package com.example.kb.retrieval;

import com.example.kb.ai.AiClient;
import com.example.kb.config.KbProperties;
import com.example.kb.vector.ChunkVector;
import com.example.kb.vector.VectorRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 检索入口，问答链路只调这一个方法。
 *
 * <p>两种模式（kb.retrieval.mode）：
 * <ul>
 *   <li>{@code vector} —— 只走向量召回。语义泛化好，但对型号、编号、专有名词召回差；</li>
 *   <li>{@code hybrid}（默认）—— 向量 + 关键词双路召回，再用 RRF 融合取 Top-K。</li>
 * </ul>
 * 保留两种模式是为了能现场做「优化前后对比」，这也是简历上那个提升数字的来源。
 */
@Slf4j
@Component
public class HybridRetriever {

    private final AiClient aiClient;
    private final VectorRepository vectorRepository;
    private final KeywordExtractor keywordExtractor;
    private final KbProperties kbProperties;

    public HybridRetriever(AiClient aiClient,
                           VectorRepository vectorRepository,
                           KeywordExtractor keywordExtractor,
                           KbProperties kbProperties) {
        this.aiClient = aiClient;
        this.vectorRepository = vectorRepository;
        this.keywordExtractor = keywordExtractor;
        this.kbProperties = kbProperties;
    }

    public List<ChunkVector> retrieve(Long kbId, String question) {
        KbProperties.Retrieval config = kbProperties.getRetrieval();
        int topK = kbProperties.getAi().getTopK();

        float[] queryVector = aiClient.embed(question);
        List<ChunkVector> vectorHits = vectorRepository.search(kbId, queryVector, config.getVectorTopK());

        if (!"hybrid".equalsIgnoreCase(config.getMode())) {
            return head(vectorHits, topK);
        }

        List<String> keywords = keywordExtractor.extract(question);
        List<ChunkVector> keywordHits = vectorRepository.searchByKeyword(
                kbId, keywords, config.getKeywordCandidateLimit());

        if (keywordHits.isEmpty()) {
            log.debug("关键词召回为空，退化为纯向量检索 keywords={}", keywords);
            return head(vectorHits, topK);
        }

        List<ChunkVector> fused = RrfFuser.fuse(config.getRrfK(), topK, vectorHits, keywordHits);
        log.debug("混合检索完成 vector={} keyword={} fused={} keywords={}",
                vectorHits.size(), keywordHits.size(), fused.size(), keywords);
        return fused;
    }

    private List<ChunkVector> head(List<ChunkVector> list, int topK) {
        return list.size() <= topK ? list : list.subList(0, topK);
    }
}
