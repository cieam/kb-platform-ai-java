-- 向量库（PostgreSQL + pgvector）
-- 维度必须和 kb.ai.embedding-dim 一致，换模型时两边一起改
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS kb_chunk_vector
(
    id        BIGSERIAL PRIMARY KEY,
    kb_id     BIGINT       NOT NULL,
    doc_id    BIGINT       NOT NULL,
    chunk_no  INT          NOT NULL,
    content   TEXT         NOT NULL,
    embedding vector(1024) NOT NULL
);

-- HNSW 召回比 IVFFlat 稳，且不需要预先训练
CREATE INDEX IF NOT EXISTS idx_chunk_vec_hnsw
    ON kb_chunk_vector USING hnsw (embedding vector_cosine_ops);

-- 多租户过滤必须走索引：检索时 WHERE kb_id = ? 前置过滤，而不是取回后再筛
CREATE INDEX IF NOT EXISTS idx_chunk_kb ON kb_chunk_vector (kb_id);
