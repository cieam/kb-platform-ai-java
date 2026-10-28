package com.example.kb.retrieval;

import com.example.kb.vector.ChunkVector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RRF（Reciprocal Rank Fusion）融合。
 *
 * <pre>
 *   score(d) = Σ 1 / (k + rank_i(d))        k 默认 60
 * </pre>
 *
 * <p>只用「排名」不用「分数」，所以不必把余弦相似度（0~1）和关键词命中数（整数）
 * 归一化到同一个量纲——这是它在工程上最省心的地方。
 *
 * <p>纯静态工具类，没有 Spring 依赖，因此可以直接写单元测试。
 */
public final class RrfFuser {

    public static final int DEFAULT_K = 60;

    private RrfFuser() {
    }

    /**
     * @param k    平滑常数，越大越弱化头部排名差异
     * @param topN 融合后保留多少条
     * @param lists 多路召回结果，按各自的相关性从高到低排列
     */
    @SafeVarargs
    public static List<ChunkVector> fuse(int k, int topN, List<ChunkVector>... lists) {
        int safeK = k <= 0 ? DEFAULT_K : k;
        Map<Long, ChunkVector> merged = new LinkedHashMap<>();
        Map<Long, Double> scores = new LinkedHashMap<>();

        for (List<ChunkVector> list : lists) {
            if (list == null) {
                continue;
            }
            for (int rank = 0; rank < list.size(); rank++) {
                ChunkVector item = list.get(rank);
                if (item == null || item.getId() == null) {
                    continue;
                }
                merged.putIfAbsent(item.getId(), item);
                scores.merge(item.getId(), 1.0 / (safeK + rank + 1), Double::sum);
            }
        }

        List<ChunkVector> result = new ArrayList<>(merged.size());
        for (Map.Entry<Long, ChunkVector> entry : merged.entrySet()) {
            ChunkVector item = entry.getValue();
            item.setScore(scores.get(entry.getKey()));
            result.add(item);
        }
        result.sort(Comparator.comparingDouble(ChunkVector::getScore).reversed());
        return result.size() <= topN ? result : new ArrayList<>(result.subList(0, topN));
    }
}
