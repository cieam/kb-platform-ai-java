package com.example.kb.vector;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * pgvector 读写。用 JdbcTemplate 而不是 ORM，因为 vector 类型没有现成的映射。
 *
 * <p>三个关键点：
 * <ol>
 *   <li>写入时把 float[] 拼成 '[0.1,0.2,...]' 再 CAST 成 vector；</li>
 *   <li>向量检索必须在 SQL 里带 {@code WHERE kb_id = ?}，多租户隔离不能放到应用层做；</li>
 *   <li>关键词召回用 2-gram + ILIKE，候选集再在内存里按命中词数排序。</li>
 * </ol>
 */
@Slf4j
@Repository
public class VectorRepository {

    private static final String INSERT_SQL =
            "INSERT INTO kb_chunk_vector (kb_id, doc_id, chunk_no, content, embedding) "
                    + "VALUES (?, ?, ?, ?, CAST(? AS vector))";

    private static final String SEARCH_SQL =
            "SELECT id, kb_id, doc_id, chunk_no, content, "
                    + "1 - (embedding <=> CAST(? AS vector)) AS score "
                    + "FROM kb_chunk_vector WHERE kb_id = ? "
                    + "ORDER BY embedding <=> CAST(? AS vector) LIMIT ?";

    private static final RowMapper<ChunkVector> VECTOR_MAPPER = (rs, rowNum) -> {
        ChunkVector chunk = new ChunkVector();
        chunk.setId(rs.getLong("id"));
        chunk.setKbId(rs.getLong("kb_id"));
        chunk.setDocId(rs.getLong("doc_id"));
        chunk.setChunkNo(rs.getInt("chunk_no"));
        chunk.setContent(rs.getString("content"));
        double score = rs.getDouble("score");
        // 零向量与任何向量的余弦距离都是 NaN：模型异常时不要让 NaN 冒到接口层
        chunk.setScore(rs.wasNull() || Double.isNaN(score) ? 0.0 : score);
        return chunk;
    };

    /** 关键词召回不需要向量分数，少读一列 */
    private static final RowMapper<ChunkVector> KEYWORD_MAPPER = (rs, rowNum) -> {
        ChunkVector chunk = new ChunkVector();
        chunk.setId(rs.getLong("id"));
        chunk.setKbId(rs.getLong("kb_id"));
        chunk.setDocId(rs.getLong("doc_id"));
        chunk.setChunkNo(rs.getInt("chunk_no"));
        chunk.setContent(rs.getString("content"));
        chunk.setScore(0.0);
        return chunk;
    };

    private final JdbcTemplate vectorJdbcTemplate;

    public VectorRepository(@Qualifier("vectorJdbcTemplate") JdbcTemplate vectorJdbcTemplate) {
        this.vectorJdbcTemplate = vectorJdbcTemplate;
    }

    public void saveAll(Long kbId, Long docId, List<String> chunks, List<float[]> vectors) {
        // 数量对不上大多是模型侧异常（比如 embedding 解析失败返回空/零向量）。
        // 在这里直接失败，比悄悄只写入前几条、留下一个"半成品知识库"要好。
        if (chunks.size() != vectors.size()) {
            throw new IllegalStateException(
                    "切片数与向量数不一致：chunks=" + chunks.size() + ", vectors=" + vectors.size());
        }
        for (int i = 0; i < chunks.size(); i++) {
            ensureValid(i, vectors.get(i));
            vectorJdbcTemplate.update(INSERT_SQL, kbId, docId, i, chunks.get(i),
                    toPgVector(vectors.get(i)));
        }
    }

    /** 零向量与任何向量的余弦距离都是 NaN，检索结果会变成 undefined，必须在入库前拦掉 */
    private static void ensureValid(int index, float[] vector) {
        if (vector == null || vector.length == 0) {
            throw new IllegalStateException("第 " + index + " 条向量为空");
        }
        boolean allZero = true;
        for (float value : vector) {
            if (Float.isNaN(value) || Float.isInfinite(value)) {
                throw new IllegalStateException("第 " + index + " 条向量包含 NaN/Infinity");
            }
            if (value != 0f) {
                allZero = false;
            }
        }
        if (allZero) {
            throw new IllegalStateException("第 " + index + " 条向量是零向量（模型返回异常）");
        }
    }

    /** 向量召回：按余弦距离升序取 Top-K，相似度换算成 1 - 距离。 */
    public List<ChunkVector> search(Long kbId, float[] queryVector, int topK) {
        String vector = toPgVector(queryVector);
        return vectorJdbcTemplate.query(SEARCH_SQL, VECTOR_MAPPER, vector, kbId, vector, topK);
    }

    /**
     * 关键词召回：任一关键词命中即进候选，再按「命中了几个关键词」排序。
     * 用 ILIKE 而不是全文索引，是因为 PostgreSQL 默认没有中文分词；
     * 生产环境应换成 zhparser 或把检索交给 Elasticsearch。
     */
    public List<ChunkVector> searchByKeyword(Long kbId, List<String> keywords, int candidateLimit) {
        if (keywords == null || keywords.isEmpty()) {
            return new ArrayList<>();
        }
        StringBuilder condition = new StringBuilder();
        List<Object> args = new ArrayList<>();
        args.add(kbId);
        for (int i = 0; i < keywords.size(); i++) {
            if (i > 0) {
                condition.append(" OR ");
            }
            condition.append("content LIKE ?");
            args.add("%" + keywords.get(i) + "%");
        }
        args.add(Math.max(1, candidateLimit));

        String sql = "SELECT id, kb_id, doc_id, chunk_no, content FROM kb_chunk_vector "
                + "WHERE kb_id = ? AND (" + condition + ") LIMIT ?";

        List<ChunkVector> candidates = vectorJdbcTemplate.query(sql, KEYWORD_MAPPER, args.toArray());
        for (ChunkVector candidate : candidates) {
            int matched = 0;
            for (String keyword : keywords) {
                if (candidate.getContent() != null && candidate.getContent().contains(keyword)) {
                    matched++;
                }
            }
            candidate.setScore((double) matched);
        }
        candidates.sort(Comparator.comparingDouble(ChunkVector::getScore).reversed());
        return candidates;
    }

    public int deleteByDocId(Long docId) {
        return vectorJdbcTemplate.update("DELETE FROM kb_chunk_vector WHERE doc_id = ?", docId);
    }

    public int countByKbId(Long kbId) {
        Integer count = vectorJdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM kb_chunk_vector WHERE kb_id = ?", Integer.class, kbId);
        return count == null ? 0 : count;
    }

    private static String toPgVector(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8 + 2);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }
}
