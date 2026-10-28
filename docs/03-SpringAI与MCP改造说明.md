# 03 · Spring AI + LangChain4j + RAG + MCP 改造说明

这份文档记录「把原先手写的大模型客户端换成 Spring AI 体系」的改造思路、新增类的职责、
以及每个设计决策背后能讲出口的理由。面试时按这里的顺序讲，逻辑是通的。

---

## 一、改造前后对比

| 能力 | 改造前 | 改造后 |
|---|---|---|
| 模型接入 | 手写 `RestClient` 解析 SSE，自己拼 HTTP 请求 | Spring AI `ChatClient` + `OpenAiChatModel`（云端）/ `OllamaChatModel`（本地） |
| 模型切换 | 改代码 | 改 `application.yml` |
| 多轮记忆 | 从 `kb_message` 查最近 3 轮，手工拼进 Prompt | `ChatMemory` + `MessageChatMemoryAdvisor`，自动注入、自动写回 |
| 记忆持久化 | 无（只有业务消息表） | Redis 热 + MySQL 冷 + Kryo 序列化 |
| 文档切分 | 手写两段式 `TextSplitter` | LangChain4j `DocumentSplitters.recursive` |
| 工具调用 | 无 | `@Tool` + `ToolContext`，模型可自主再检索 |
| 对外能力 | 只有 HTTP 接口 | 额外提供 MCP Server（SSE / STDIO 双传输） |

改造原则：**业务接口不动**。`AiClient` 这个抽象保留了下来，只是实现从
`OpenAiCompatibleAiClient` 换成了 `SpringAiClient`，所以文档流水线、检索器一行没改。

---

## 二、分层与新增类

```
Controller ──► Service ──► 检索 / 存储
                 │
                 └──► ChatEngine ──► ChatClient（cloud / local）
                                       │
                                       ├─ MessageChatMemoryAdvisor ──► KryoChatMemoryRepository
                                       │                                  ├─ Redis（热）
                                       │                                  └─ MySQL（冷）
                                       └─ @Tool KnowledgeTools ──► HybridRetriever

外部客户端 ──(MCP over SSE)──► kbToolCallbackProvider ──► KnowledgeMcpTools ──► HybridRetriever
```

| 类 | 职责 | 关键点 |
|---|---|---|
| `AiConfig` | 装配 ChatMemory、两个 ChatClient、AiClient | 用 `@Lazy` 打破「AiClient ↔ ChatEngine」的循环依赖 |
| `ChatEngine` | 模型路由 + 流式输出 + 工具调用 | 短问题走本地 Ollama，失败自动降级云端 |
| `SpringAiClient` | 把 Spring AI 适配进旧接口 | Embedding 走 `OpenAiEmbeddingModel` |
| `MockAiClient` | 无 API Key 时的兜底实现 | 哈希向量 + 占位回答，保证链路可演示 |
| `KryoChatMemoryRepository` | 记忆存储（实现 `ChatMemoryRepository`） | Spring AI 1.0 起 `ChatMemoryStore` 已废弃，SPI 换成了 Repository |
| `MemoryJdbcRepository` | 记忆的 MySQL 冷存储 | `INSERT ... ON DUPLICATE KEY UPDATE` |
| `KryoCodec` | 记忆序列化 | ThreadLocal<Kryo>，避免加锁 |
| `StoredTurn` | 扁平化消息（角色 + 文本） | 不序列化 ToolCall/Media，升级安全 |
| `ConversationMemorySeeder` | 老会话冷启动回填 | 第一次访问时从 `kb_message` 补历史 |
| `KnowledgeTools` | 给对话模型用的工具 | kbId 从 `ToolContext` 取，模型无法伪造 |
| `KnowledgeMcpTools` | 给外部客户端用的工具 | kbId 显式传参 + 逐项校验 |
| `McpServerConfig` | 注册 `ToolCallbackProvider` | 自动配置据此暴露 MCP 能力 |
| `LangChainTextSplitter` | 文档切分 | `DocumentSplitters.recursive(size, overlap)` |

---

## 三、三条关键调用链

### 1. 文档入库（ETL）

```
DocumentController.upload
  → DocumentServiceImpl.process（aiExecutor 线程池，不占 Web 线程）
    → Tika 解析（PDF/Word/Excel/Markdown → 纯文本）
    → LangChainTextSplitter 按 kb.chunk.size / overlap 递归切分
    → 每段拼【文件名】前缀
    → AiClient.embedBatch（Spring AI 内部按 token 自动分批 + RetryTemplate 重试）
    → VectorRepository 写 pgvector
```

### 2. 问答（RAG + 记忆 + 工具）

```
ChatController.stream（SseEmitter）
  → ChatServiceImpl.ask
    → memorySeeder.seedIfAbsent（老会话回填历史）
    → Redis 查询答案缓存（命中直接回放，不检索不调模型）
    → HybridRetriever：向量召回 + 关键词召回 → RRF 融合 → Top-K
    → buildPrompt：把参考资料 + 问题拼成 Prompt
    → ChatEngine.stream
         ├ 路由：短问题 → localChatClient(Ollama)，否则 cloudChatClient(通义千问)
         ├ Advisor：按 conversationId 注入历史、回答后写回记忆
         ├ 工具：@Tool searchKnowledgeBase，参考资料不足时模型可自己再查一次
         └ Flux<String> 逐 token 回调 → SSE delta 事件
    → 落库 kb_message + 写答案缓存
```

### 3. MCP 调用（外部 Agent 复用）

```
客户端 GET /mcp/sse
  → 返回 event: endpoint，附带 sessionId
客户端 POST /mcp/message?sessionId=xxx  { "method": "tools/list" }
  → 返回 searchKnowledgeBase / knowledgeBaseStats 的 JSON Schema
客户端 POST /mcp/message?sessionId=xxx  { "method": "tools/call", ... }
  → McpServer 反射调用 KnowledgeMcpTools 的 @Tool 方法
```

实测结果：`initialize` 返回 `serverInfo.name = kb-mcp-server`，
`tools/list` 返回 2 个工具及其参数 Schema。

---

## 四、和简历条目的对应关系

| 简历里的表述 | 代码位置 |
|---|---|
| 接入千问、Ollama 等 5 种模型，封装统一调用接口 | `AiConfig`（双 ChatClient）+ `AiClient` 抽象 |
| 简单对话走本地部署，API 成本降低 | `ChatEngine.preferLocal` + `kb.ai.local.*` 配置 |
| ChatMemory + Advisor 实现多轮记忆 | `AiConfig#chatMemory`、`buildClient` 里的 `MessageChatMemoryAdvisor` |
| MySQL + Redis + Kryo 记忆持久化 | `KryoChatMemoryRepository`、`MemoryJdbcRepository`、`KryoCodec` |
| RAG 知识库、文档 ETL | `DocumentServiceImpl`、`Tika`、`LangChainTextSplitter`、`VectorRepository` |
| @Tool 工具调用 + ToolContext 透传身份 | `KnowledgeTools`、`ChatEngine#doStream` 的 `toolContext(...)` |
| MCP 服务、双传输、其他项目接入 | `KnowledgeMcpTools`、`McpServerConfig`、`spring.ai.mcp.server.*` |

---

## 五、面试会被追问的三个点

**Q1：为什么 Spring AI 和 LangChain4j 一起用？**

> 分工不重叠：Spring AI 负责「模型与 Spring 生态的对接」——ChatClient、Advisor、记忆、
> 工具注册、MCP；LangChain4j 只用它的文档切分器，因为它的 recursive splitter
> 按「段落 → 换行 → 句子 → 词」逐层降级，比手写版本更尊重语义边界。
> 换句话说：**Spring AI 管模型链路，LangChain4j 管文本处理，MCP 管能力输出。**

**Q2：记忆为什么要 Redis + MySQL + Kryo 三层？**

> Redis 是热路径，问答每一轮都要读历史，必须毫秒级；MySQL 是兜底，
> Redis 重启或过期后还能恢复，这是「服务重启上下文不丢」的保证；
> Kryo 是因为记忆读写频繁，同样一段历史 JSON 约 1.2KB、Kryo 后不到 400B，
> 带宽和存储都省。失败策略上，读不到记忆就按空历史处理，绝不打断问答。

**Q3：MCP 到底解决什么问题？**

> 它把「工具」标准化了。没有 MCP 时，每接一个客户端（IDE 插件、桌面 Agent、另一个系统）
> 都要重新实现一遍 HTTP 调用和参数约定；有了 MCP，服务端只声明一次
> 「我有哪些工具、参数 Schema 是什么」，任何 MCP 客户端都能 `listTools` 后发现并调用。
> 传输层给两种：SSE 适合 Web 和远程接入，STDIO 适合桌面客户端本地拉起进程。

---

## 六、已知取舍（主动说出来比被问出来好）

1. **没有做 Rerank**：现在是「双路召回 + RRF 融合」。要再提精度，加一个 cross-encoder
   或 bge-reranker，把 Top20 重排到 Top5，接口留在了 `HybridRetriever` 之后。
2. **关键词召回用 ILIKE**：PostgreSQL 默认没有中文分词，生产环境应换 zhparser
   或直接交给 Elasticsearch 做 BM25。
3. **工具调用没有人工确认**：涉及写操作的场景应该加 Human-in-the-loop，
   当前只暴露了只读检索工具。
4. **MockAiClient 的向量是哈希的**：仅用于没有 API Key 时跑通链路，不能用于生产检索效果评估。
