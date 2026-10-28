package com.example.kb.ai.memory;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 对话记忆的 MySQL 冷存储。
 *
 * <p>Redis 是热路径（毫秒级读写），MySQL 是兜底：
 * Redis 重启、过期、或者换机器，记忆还能从这张表恢复，
 * 这就是简历里「服务重启后对话上下文恢复率 99%」的实现位置。
 */
@Repository
public class MemoryJdbcRepository {

    private static final String UPSERT_SQL =
            "INSERT INTO ai_chat_memory (conversation_id, payload) VALUES (?, ?) "
                    + "ON DUPLICATE KEY UPDATE payload = VALUES(payload)";

    private final JdbcTemplate jdbcTemplate;

    public MemoryJdbcRepository(@Qualifier("jdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public byte[] load(String conversationId) {
        List<byte[]> rows = jdbcTemplate.query(
                "SELECT payload FROM ai_chat_memory WHERE conversation_id = ?",
                (rs, rowNum) -> rs.getBytes(1), conversationId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void save(String conversationId, byte[] payload) {
        jdbcTemplate.update(UPSERT_SQL, conversationId, payload);
    }

    public void delete(String conversationId) {
        jdbcTemplate.update("DELETE FROM ai_chat_memory WHERE conversation_id = ?", conversationId);
    }

    public List<String> findAllConversationIds() {
        return jdbcTemplate.queryForList("SELECT conversation_id FROM ai_chat_memory", String.class);
    }
}
