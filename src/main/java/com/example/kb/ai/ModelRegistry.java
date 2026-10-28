package com.example.kb.ai;

import com.example.kb.config.KbProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多模型注册表：把「5 种模型」从配置变成真正的对象。
 *
 * <p>配置在 {@code kb.ai.providers} 下，每个条目描述一个 OpenAI 兼容端点或 Ollama。
 * 启动时按 priority 排序逐个构建 ChatClient：
 * <ul>
 *   <li>没配 apiKey 的直接跳过（不报错），所以只填一个 Key 也能跑；</li>
 *   <li>type=ollama 的作为本地小模型，只保留一个，用于简单问题分流；</li>
 *   <li>其余构成云端降级链，前面的失败自动切下一个。</li>
 * </ul>
 *
 * <p>为什么用编程式构建而不是 starter：starter 只能装配一个 OpenAiChatModel，
 * 多 provider 必须自己 new。Embedding 仍然只保留一个，
 * 因为向量维度必须和 pgvector 表结构严格一致，多模型混用会写坏向量库。
 */
@Slf4j
@Component
public class ModelRegistry {

    private final Map<String, ChatClient> clients = new LinkedHashMap<>();
    private final List<ChatClient> cloudChain = new ArrayList<>();
    private ChatClient localClient;

    private final KbProperties properties;
    private final ChatMemory chatMemory;
    private final OllamaChatModel ollamaChatModel;
    private final OpenAiChatModel defaultOpenAiChatModel;

    public ModelRegistry(KbProperties properties,
                         ChatMemory chatMemory,
                         OllamaChatModel ollamaChatModel,
                         OpenAiChatModel defaultOpenAiChatModel) {
        this.properties = properties;
        this.chatMemory = chatMemory;
        this.ollamaChatModel = ollamaChatModel;
        this.defaultOpenAiChatModel = defaultOpenAiChatModel;
    }

    @PostConstruct
    public void init() {
        List<KbProperties.Provider> providers = new ArrayList<>(properties.getAi().getProviders());
        providers.sort(Comparator.comparingInt(KbProperties.Provider::getPriority));

        for (KbProperties.Provider provider : providers) {
            if (!provider.isEnabled()) {
                continue;
            }
            try {
                if ("ollama".equalsIgnoreCase(provider.getType())) {
                    localClient = buildClient(ollamaChatModel);
                    clients.put(provider.getName(), localClient);
                    log.info("模型注册成功：{}（ollama / {}）", provider.getName(), provider.getModel());
                    continue;
                }
                if (provider.getApiKey() == null || provider.getApiKey().isBlank()) {
                    log.info("跳过未配置 Key 的模型：{}", provider.getName());
                    continue;
                }
                OpenAiApi api = OpenAiApi.builder()
                        .baseUrl(provider.getBaseUrl())
                        .apiKey(provider.getApiKey())
                        .build();
                OpenAiChatOptions options = OpenAiChatOptions.builder()
                        .model(provider.getModel())
                        .temperature(provider.getTemperature())
                        .build();
                OpenAiChatModel model = OpenAiChatModel.builder()
                        .openAiApi(api)
                        .defaultOptions(options)
                        .build();
                ChatClient client = buildClient(model);
                clients.put(provider.getName(), client);
                cloudChain.add(client);
                log.info("模型注册成功：{}（{} / {}）", provider.getName(), provider.getBaseUrl(), provider.getModel());
            } catch (Exception e) {
                log.warn("模型初始化失败，已跳过：{} -> {}", provider.getName(), e.getMessage());
            }
        }

        // 兜底：没有配置 providers 时，沿用 starter 装配的默认模型，保证老配置仍可用
        if (clients.isEmpty()) {
            cloudChain.add(buildClient(defaultOpenAiChatModel));
            localClient = buildClient(ollamaChatModel);
            log.warn("kb.ai.providers 为空，回退到 starter 默认模型");
        }
        log.info("模型注册完成：云端 {} 个，本地 {}，降级链长度 {}",
                clients.size() - (localClient == null ? 0 : 1),
                localClient == null ? "无" : "1",
                cloudChain.size());
    }

    /** 云端降级链：按 priority 从高到低 */
    public List<ChatClient> cloudChain() {
        return cloudChain;
    }

    public ChatClient local() {
        return localClient;
    }

    public Map<String, ChatClient> all() {
        return clients;
    }

    private ChatClient buildClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(AiConfig.SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }
}
