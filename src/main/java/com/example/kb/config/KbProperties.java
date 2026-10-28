package com.example.kb.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * kb.* 下的全部自定义配置，集中一处便于查找。
 */
@Data
@Component
@ConfigurationProperties(prefix = "kb")
public class KbProperties {

    private Jwt jwt = new Jwt();
    private Ai ai = new Ai();
    private Memory memory = new Memory();
    private Retrieval retrieval = new Retrieval();
    private Cache cache = new Cache();
    private Chunk chunk = new Chunk();
    private Storage storage = new Storage();

    @Data
    public static class Jwt {
        private String secret;
        private long expireMinutes = 720;
    }

    @Data
    public static class Ai {
        private String baseUrl;
        private String apiKey;
        private String chatModel;
        private String embeddingModel;
        private int embeddingDim = 1024;
        /** 一次请求算多少条向量 */
        private int embeddingBatchSize = 16;
        /** 单批失败后的最大重试次数（指数退避） */
        private int maxRetries = 3;
        /** 最终注入 Prompt 的切片数量 */
        private int topK = 5;
        /** 本地小模型（Ollama）：简单问题走它，省云端 API 费用 */
        private Local local = new Local();
        /** 多模型注册表：按优先级排列，未配置 Key 的会自动跳过 */
        private List<Provider> providers = new ArrayList<>();

        /** 没配 Key 时走本地模拟，保证工程能跑起来。 */
        public boolean mock() {
            return apiKey == null || apiKey.isBlank();
        }
    }

    /**
     * 一个可用的对话模型。
     *
     * <p>type=openai 走 OpenAI 兼容协议（千问 / DeepSeek / Kimi / OpenAI 都是这套），
     * type=ollama 走本地 Ollama。priority 越小越优先，作为降级顺序。
     */
    @Data
    public static class Provider {
        private String name;
        private String type = "openai";
        private String baseUrl;
        private String apiKey;
        private String model;
        private double temperature = 0.3;
        private int priority = 10;
        private boolean enabled = true;
    }

    @Data
    public static class Local {
        private boolean enabled = true;
        private String model = "qwen2.5:3b";
        /** 问题长度不超过这个值、且不含复杂意图词，才交给本地模型 */
        private int maxQuestionLength = 40;
    }

    /**
     * 对话记忆：Spring AI 的 ChatMemory 负责窗口裁剪，这里配的是持久化与注入参数。
     */
    @Data
    public static class Memory {
        /** 注入 Prompt 的最近消息条数，对应 MessageWindowChatMemory#maxMessages */
        private int window = 20;
        /** Redis 中记忆的存活时间（分钟） */
        private int ttlMinutes = 120;
        private String redisPrefix = "kb:memory:";
        /** 冷启动时从 kb_message 回填的历史条数 */
        private int seedMessages = 6;
    }

    @Data
    public static class Retrieval {
        /** vector：只走向量召回；hybrid：向量 + 关键词双路召回后 RRF 融合 */
        private String mode = "hybrid";
        /** 向量召回取多少条候选 */
        private int vectorTopK = 20;
        /** 关键词召回取多少条候选 */
        private int keywordTopK = 20;
        /** RRF 公式里的 k，越大越平滑 */
        private int rrfK = 60;
        /** 关键词召回的 SQL 候选上限，防止 LIKE 扫太多行 */
        private int keywordCandidateLimit = 200;
    }

    @Data
    public static class Cache {
        /** 问答缓存：同一个知识库里问过一模一样的问题，直接回放，不再检索也不再调模型 */
        private Answer answer = new Answer();

        @Data
        public static class Answer {
            private boolean enabled = true;
            private int ttlMinutes = 30;
        }
    }

    @Data
    public static class Chunk {
        private int size = 800;
        private int overlap = 150;
    }

    @Data
    public static class Storage {
        private String type = "local";
        private String localPath = "./data/files";
    }
}
