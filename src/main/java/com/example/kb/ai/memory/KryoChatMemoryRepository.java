package com.example.kb.ai.memory;

import com.example.kb.config.AsyncConfig;
import com.example.kb.config.KbProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Spring AI 的对话记忆存储：Redis 热 + MySQL 冷 + Kryo 序列化。
 *
 * <p>读写策略：
 * <ul>
 *   <li>读 —— 先 Redis，没有再读 MySQL 并回填 Redis（冷启动恢复）；</li>
 *   <li>写 —— 同步写 Redis（保证下一轮立刻可读），异步落 MySQL（不拖慢对话主链路）；</li>
 *   <li>失败 —— 任何存储异常都降级成「没有历史」，绝不让记忆问题打断问答。</li>
 * </ul>
 *
 * <p>注意：Spring AI 1.0 起持久化 SPI 从 ChatMemoryStore 改成了 ChatMemoryRepository，
 * 方法语义从「覆盖整段记忆」变成「按会话保存 / 查询 / 删除」，窗口裁剪由 ChatMemory 负责。
 */
@Slf4j
@Component
public class KryoChatMemoryRepository implements ChatMemoryRepository {

    private final StringRedisTemplate redisTemplate;
    private final MemoryJdbcRepository jdbcRepository;
    private final KryoCodec codec;
    private final KbProperties properties;
    private final ThreadPoolTaskExecutor aiExecutor;

    public KryoChatMemoryRepository(StringRedisTemplate redisTemplate,
                                    MemoryJdbcRepository jdbcRepository,
                                    KryoCodec codec,
                                    KbProperties properties,
                                    @Qualifier(AsyncConfig.AI_EXECUTOR) ThreadPoolTaskExecutor aiExecutor) {
        this.redisTemplate = redisTemplate;
        this.jdbcRepository = jdbcRepository;
        this.codec = codec;
        this.properties = properties;
        this.aiExecutor = aiExecutor;
    }

    @Override
    public List<String> findConversationIds() {
        try {
            return jdbcRepository.findAllConversationIds();
        } catch (Exception e) {
            log.warn("查询记忆会话列表失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<Message> findByConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return new ArrayList<>();
        }
        try {
            String cached = redisTemplate.opsForValue().get(key(conversationId));
            byte[] payload = cached != null
                    ? Base64.getDecoder().decode(cached)
                    : jdbcRepository.load(conversationId);

            if (payload == null || payload.length == 0) {
                return new ArrayList<>();
            }
            // 从 MySQL 命中时顺手回填 Redis，下一次就不再走数据库
            if (cached == null) {
                redisTemplate.opsForValue().set(key(conversationId),
                        Base64.getEncoder().encodeToString(payload), ttl());
            }

            List<Message> messages = new ArrayList<>();
            for (StoredTurn turn : codec.decode(payload)) {
                Message message = turn.toMessage();
                if (message != null) {
                    messages.add(message);
                }
            }
            return messages;
        } catch (Exception e) {
            log.warn("读取对话记忆失败，按空历史处理 conversationId={}", conversationId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (conversationId == null || messages == null) {
            return;
        }
        try {
            byte[] payload = codec.encode(StoredTurn.from(messages));
            redisTemplate.opsForValue().set(key(conversationId),
                    Base64.getEncoder().encodeToString(payload), ttl());
            // 落库放到线程池：记忆是旁路数据，不值得让用户等一次 MySQL 往返
            aiExecutor.execute(() -> {
                try {
                    jdbcRepository.save(conversationId, payload);
                } catch (Exception e) {
                    log.warn("对话记忆落库失败 conversationId={}", conversationId, e);
                }
            });
        } catch (Exception e) {
            log.warn("保存对话记忆失败 conversationId={}", conversationId, e);
        }
    }

    @Override
    public void deleteByConversationId(String conversationId) {
        try {
            redisTemplate.delete(key(conversationId));
            jdbcRepository.delete(conversationId);
        } catch (Exception e) {
            log.warn("删除对话记忆失败 conversationId={}", conversationId, e);
        }
    }

    private String key(String conversationId) {
        return properties.getMemory().getRedisPrefix() + conversationId;
    }

    private Duration ttl() {
        return Duration.ofMinutes(properties.getMemory().getTtlMinutes());
    }
}
