# kb-platform · 企业级 AI 知识库问答系统

[![CI](https://github.com/USER/kb-platform/actions/workflows/ci.yml/badge.svg)](https://github.com/USER/kb-platform/actions/workflows/ci.yml)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-brightgreen)
![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0-6DB33F)
![License](https://img.shields.io/badge/license-MIT-blue)

> 上传文档 → 自动解析切分 → 向量化入库 → 混合检索 → 大模型生成**带出处**的回答。
> 后端基于 Spring Boot 3 + Spring AI + LangChain4j + pgvector，并提供 **MCP Server** 把知识库能力开放给其他 AI 客户端。

## 功能特性

| 能力 | 说明 |
|---|---|
| 文档 ETL | Tika 解析 PDF / Word / Excel / Markdown，LangChain4j 递归切分，pgvector 存储 |
| 混合检索 | 向量召回 + 关键词召回，RRF 融合排序，解决型号、专有名词召回差的问题 |
| 引用溯源 | 每个回答都带文件名、切片号、相似度与原文片段 |
| 多轮记忆 | Spring AI `ChatMemory` + `MessageChatMemoryAdvisor`，Redis 热存 + MySQL 冷存 + Kryo 序列化 |
| 多模型 | 千问 / DeepSeek / Kimi / OpenAI / Ollama 五个 provider，按优先级降级，简单问题走本地 |
| 工具调用 | `@Tool` + `ToolContext`，模型可在资料不足时自主再检索一次 |
| MCP Server | SSE 传输，`searchKnowledgeBase` / `knowledgeBaseStats` 两个工具，供外部 Agent 复用 |
| 流式输出 | SSE 逐字返回，前端 fetch + ReadableStream 消费 |
| 多用户隔离 | JWT 鉴权 + 知识库归属校验，越权访问返回 403 |

## 系统架构

```mermaid
flowchart LR
    A[设备/用户] -->|上传文档| B[DocumentController]
    B --> C[ETL: Tika + LangChain4j]
    C --> D[(pgvector)]
    A -->|提问 SSE| E[ChatController]
    E --> F[HybridRetriever<br/>向量 + 关键词 + RRF]
    F --> D
    E --> G[ChatEngine]
    G --> H[ModelRegistry<br/>5 个 provider 降级链]
    H --> I[ChatClient + ChatMemory Advisor]
    I --> J[(Redis 热 / MySQL 冷)]
    G -.->|@Tool| F
    K[MCP 客户端] -->|SSE| L[McpServer]
    L --> F
```

## 快速开始

```bash
# 1. 起中间件（MySQL 需自备或 docker compose --profile with-mysql up -d）
docker compose up -d redis postgres

# 2. 配本地环境（模板不含任何真实口令）
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml

# 3. 启动
mvn spring-boot:run
```

访问 <http://localhost:8080/>，演示账号 `admin / 123456`。
想接真实模型，把环境变量配上即可（不配则自动降级为本地模拟实现，链路依然可跑通）：

```bash
export DASHSCOPE_API_KEY=sk-xxx     # 千问
export DEEPSEEK_API_KEY=sk-xxx      # 可选：第二个 provider
export KB_AI_API_KEY=sk-xxx         # 业务侧判断是否走模拟实现
```

或者容器化一键启动（把中间件和应用一起拉起）：

```bash
docker compose --profile with-mysql --profile app up -d
# → http://localhost:8080/
```

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 / 框架 | Java 17、Spring Boot 3.4、Spring Security（JWT） |
| AI | Spring AI 1.0（ChatClient / Advisor / Tool / MCP）、LangChain4j（文档切分） |
| 存储 | MySQL 8（业务 + 记忆冷存）、Redis 7（缓存 + 记忆热存）、PostgreSQL 16 + pgvector（向量） |
| 文档解析 | Apache Tika 2.9 |
| 序列化 | Jackson、Kryo 5 |
| 前端 | Vue 3 + Element Plus + Vite（仓库 [kb-web](https://github.com/USER/kb-web)） |

## 目录结构

```
kb-platform/
├── src/main/java/com/example/kb/
│   ├── ai/            # 模型接入、路由、记忆、工具（Spring AI 相关都在这）
│   ├── mcp/           # MCP Server 与对外工具
│   ├── retrieval/     # 混合检索与 RRF 融合
│   ├── vector/        # pgvector 读写
│   ├── service/       # 业务编排（文档、问答、知识库）
│   ├── controller/    # REST / SSE 接口
│   └── config/        # 数据源、鉴权、线程池、配置项
├── docs/              # 模块说明、工作流程、改造记录
├── sql/  sql-pg/      # MySQL 与 PostgreSQL 建表脚本
└── tools/             # 本地 mock 大模型服务（无 Key 联调用）
```

## 文档

| 文件 | 内容 |
|---|---|
| [`docs/01-模块与类说明.md`](docs/01-模块与类说明.md) | 分层职责与核心类 |
| [`docs/02-工作流程.md`](docs/02-工作流程.md) | 上传、检索、问答的完整调用链 |
| [`docs/03-SpringAI与MCP改造说明.md`](docs/03-SpringAI与MCP改造说明.md) | Spring AI / MCP 改造的设计取舍 |
| 下文 | 环境搭建、接口说明、排错手册 |

---

## 一、文档在哪

| 文件 | 内容 |
|---|---|
| [`docs/01-模块与类说明.md`](docs/01-模块与类说明.md) | 每个模块、每个类的作用、关键方法、设计意图 |
| [`docs/02-工作流程.md`](docs/02-工作流程.md) | 登录、上传解析、混合检索、RAG 问答等流程的完整调用链 |
| 各包的 `package-info.java` | 模块职责写在代码里，IDE 里悬停即可看到 |
| 本文件 | 怎么跑起来、有哪些接口、出问题怎么查 |

## 二、环境要求

| 组件 | 版本 | 说明 |
|---|---|---|
| JDK | 17 | `JAVA_HOME` 指向 jdk-17 |
| Maven | 3.6+ | 本地仓库建议配阿里云镜像 |
| Docker | 任意较新版本 | 用来起 MySQL / Redis / PostgreSQL |

## 三、启动步骤

```bash
# 1. 起三个中间件（MySQL 自动执行 sql/init.sql，PostgreSQL 自动执行 sql-pg/init.sql）
docker compose up -d

# 2. 确认三个容器都是 running
docker compose ps

# 3. 启动应用
mvn spring-boot:run
```

启动后：

- **前端页面**：<http://localhost:8080/> —— Vue 3 单页应用，登录 / 知识库 / 文档 / 问答四个界面。
  源码在同级目录 `kb-web`，`pnpm build` 的产物会输出到本工程的 `src/main/resources/static`
- **接口文档**：<http://localhost:8080/doc.html>
- **演示账号**：`admin / 123456`（由 `DataInitializer` 在首次启动时创建）

> 改了 `sql/init.sql` 之后必须 `docker compose down -v` 再 `up`，否则初始化脚本不会重跑。

## 四、配置大模型（可选，不配也能跑）

默认读取环境变量：

```bash
set KB_AI_API_KEY=sk-xxxxxxxx
set KB_AI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
set KB_AI_CHAT_MODEL=qwen-plus
set KB_AI_EMBEDDING_MODEL=text-embedding-v3

# 用 DeepSeek 也可以，Base URL 换成 https://api.deepseek.com
```

**不配 Key 会怎样**：`OpenAiCompatibleAiClient` 自动降级为本地模拟实现 ——
Embedding 用哈希向量（字面重叠的文本余弦相似度确实更高），对话返回固定占位文本并模拟逐字输出。
「上传 → 切分 → 召回 → RRF 融合 → 流式问答 → 引用来源」整条链路仍然真实执行，
只是最终答案不是模型产出的。

## 五、接口清单

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册 |
| POST | `/api/auth/login` | 登录，返回 JWT |
| GET | `/api/auth/me` | 当前用户 |
| POST | `/api/kb` | 新建知识库 |
| PUT | `/api/kb` | 修改知识库 |
| DELETE | `/api/kb/{id}` | 删除知识库 |
| GET | `/api/kb/{id}` | 知识库详情 |
| GET | `/api/kb/page` | 我的知识库（分页） |
| POST | `/api/doc/upload` | 上传文档（form-data: kbId, file），立即返回 |
| GET | `/api/doc/page` | 文档列表，用 `status` 判断解析进度 |
| GET | `/api/doc/{id}` | 文档详情 |
| POST | `/api/doc/{id}/reparse` | **重新解析**（失败后不用重新上传） |
| DELETE | `/api/doc/{id}` | 删除文档并清理向量 |
| POST | `/api/chat/conversation` | 新建会话 |
| GET | `/api/chat/conversation` | 会话列表 |
| GET | `/api/chat/message` | 会话消息 |
| POST | `/api/chat/ask` | 普通问答（等全部生成完） |
| POST | `/api/chat/stream` | **流式问答（SSE）** |

除 `/api/auth/**`、`/`、`/index.html` 和接口文档外，所有请求都要带
`Authorization: Bearer <token>`。

### 流式接口的事件格式

```
event:delta
data:{"t":"年假"}

event:delta
data:{"t":"为 10 天"}

event:references
data:[{"docId":1,"fileName":"员工手册.pdf","chunkNo":3,"score":0.83,"content":"..."}]

event:done
data:{"conversationId":1}
```

payload 一律是 JSON：裸字符串里的换行会破坏 SSE 的 `data:` 分帧。

### 快速自测

```bash
# 登录取 token
curl -X POST http://localhost:8080/api/auth/login ^
  -H "Content-Type: application/json" ^
  -d "{\"username\":\"admin\",\"password\":\"123456\"}"

# 建知识库
curl -X POST http://localhost:8080/api/kb ^
  -H "Content-Type: application/json" -H "Authorization: Bearer <token>" ^
  -d "{\"name\":\"员工手册\",\"description\":\"测试\"}"

# 传文档
curl -X POST http://localhost:8080/api/doc/upload ^
  -H "Authorization: Bearer <token>" ^
  -F "kbId=1" -F "file=@D:/员工手册.pdf"

# 看解析结果（status=SUCCESS 表示切片和向量都已入库）
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/doc/1

# 流式问答（-N 关闭缓冲，才能看到逐字输出）
curl -N -X POST http://localhost:8080/api/chat/stream ^
  -H "Content-Type: application/json" -H "Authorization: Bearer <token>" ^
  -d "{\"kbId\":1,\"question\":\"请假流程是什么\"}"
```

## 六、目录结构

```
kb-platform                        后端（单体）
├── docs/                        模块说明与工作流程（先看这里）
├── sql/init.sql                 MySQL 建表脚本
├── sql-pg/init.sql              pgvector 建表脚本
├── docker-compose.yml           MySQL + Redis + PostgreSQL
└── src
    ├── main/java/com/example/kb
    │   ├── common               统一返回、异常、工具（JwtUtil / UserContext / TextSplitter）
    │   ├── config               数据源、MyBatis-Plus、Redis、线程池、Security、OpenAPI、配置类
    │   ├── security             JWT 过滤器
    │   ├── domain               entity（表）/ dto（入参）/ vo（出参）
    │   ├── mapper               MyBatis-Plus BaseMapper
    │   ├── vector               pgvector 读写
    │   ├── retrieval            检索策略：关键词抽取、RRF 融合、混合检索入口
    │   ├── ai                   大模型抽象与 OpenAI 兼容实现
    │   ├── service              业务逻辑，事务边界在这一层
    │   ├── controller           只做参数校验和调用 service
    │   └── init                 启动时初始化演示账号
    ├── main/resources
    │   ├── application.yml      全部配置
    │   └── static/index.html    单页前端（登录 / 上传 / 聊天）
    └── test/java/com/example/kb 单元测试
```

前端在**同级目录** `kb-web`（Vue 3 + Vite + Element Plus），构建产物输出到上面的 `static` 目录。

## 七、检索策略怎么调

`kb.retrieval.mode` 有两个取值，想对比效果时就改这里：

| 值 | 行为 | 适用 |
|---|---|---|
| `vector` | 只走向量召回 Top-K | 语义泛化的问题（「怎么请假」↔「休假申请流程」） |
| `hybrid`（默认） | 向量 Top20 + 关键词 Top20 → RRF 融合 → 取 Top5 | 含型号、编号、专有名词的问题 |

想演示「优化前后对比」：先设成 `vector` 跑几个问题记下命中情况，再改成 `hybrid` 重跑，
这就是面试时那个「Top-5 命中率从 X 提升到 Y」的故事来源（**数字要自己测**）。

## 八、已知限制（面试时主动说，比被追问要好）

- 关键词召回用的是「2-gram + LIKE」，没有中文分词器。生产环境应换成 zhparser /
  Elasticsearch / Milvus 混合检索。
- 会话上下文只按「最近 3 轮」截断，没有做摘要压缩，超长对话会丢早期信息。
- 没有重排（rerank）模型，融合直接取 Top5。
- 没有限流和 Token 配额，生产环境要补。
- 扫描件 PDF 只做识别和提示，不做 OCR。
- 向量维度写死在 `sql-pg/init.sql` 的 `vector(1024)`，换 Embedding 模型时两处要一起改。
- 删除文档只清理向量与本地文件，历史消息里的引用快照会保留（这是有意的，保证历史可追溯）。

## 九、常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| 启动报连不上 MySQL | 容器没起来或端口被本机 MySQL 占用 | `docker compose ps`，确认 3306 空闲 |
| 上传后一直 PENDING | 线程池排队 | 稍等；若长时间不动看日志 |
| `status=FAILED`，error_msg 提到扫描件 | 图片型 PDF | 需要 OCR，当前不支持；换一份文本型 PDF |
| 问答报 pgvector 相关错误 | PostgreSQL 没起或扩展没装 | `docker exec -it kb-postgres psql -U postgres -d kb_vector -c "\dx"` |
| 问什么都答「未配置 API Key」 | 没配 `KB_AI_API_KEY` | 见第四节；不影响链路验收 |
| 接口全部 401 | 没带 token 或 token 过期 | 重新登录；默认有效期 12 小时 |
| **非流式问答正常，流式问答返回 401** | SSE 把业务丢进了线程池，而登录用户存在 ThreadLocal 里，新线程读不到 | 线程池任务必须用 `UserContext.wrap(...)` 包一层（`ChatController.stream` 已这么写）。详见 `docs/02-工作流程.md` 第五节 |
| 想问的问题重复问，第二次明显更快 | 这不是 bug，是命中了 Redis 问答缓存 | 阈值和开关见 `kb.cache.answer.*`；`redis-cli KEYS 'kb:answer:*'` 能看到缓存 |
| 前端页面打开是 404 | 静态资源被 Security 拦了 | 确认 `SecurityConfig` 的 permitAll 里有 `/` 和 `/index.html` |

## 十、想换成 Spring AI 怎么办

业务层只依赖 `AiClient` 接口，替换成本很低：

1. `pom.xml` 里加 `spring-ai-openai-spring-boot-starter`（版本按官网对应表选）；
2. 新写一个 `SpringAiClient implements AiClient`，`embed` 用 `EmbeddingModel`，
   `chatStream` 用 `ChatClient.prompt().stream().content()`；
3. 给 `OpenAiCompatibleAiClient` 加上 `@ConditionalOnMissingBean`。

这样简历上的「Spring AI」就是真的，而且你能讲清「换实现只动了一个类」。


## 十一、前端工程（kb-web）

前端是独立的 Vue 3 工程，位于同级目录 `kb-web`。

| 项 | 说明 |
|---|---|
| 技术栈 | Vue 3（`<script setup>`）+ Vite 5 + Element Plus 2 + Pinia + Vue Router 4 + Axios + markdown-it |
| 界面 | 登录/注册、知识库管理、文档管理、智能问答 四个页面 |
| 开发 | `cd ../kb-web && pnpm install && pnpm dev` → <http://localhost:5173>，`/api` 自动代理到 8080 |
| 构建 | `cd ../kb-web && pnpm build` → 产物直接写进本工程 `src/main/resources/static` |

**打包顺序**：先 `pnpm build`（前端），再 `mvn clean package`（后端），一个 jar 就是完整应用，
不需要 Nginx，也不需要配 CORS。

几个和流式问答相关的实现点：

- SSE 用 `fetch` + `ReadableStream` 消费，**不用 EventSource**（它发不了 POST body）。
- 流式渲染做了 60ms 节流：每个 token 都重跑 Markdown 渲染的话，长回答会明显卡顿。
- 「停止生成」用 `AbortController` 中断 fetch，后端 `SseEmitter.send` 抛异常后中断本次生成。
- 引用来源支持 hover 看原文片段，这是「答案可溯源」的落点。
- 路由用 history 模式，后端有 `SpaForwardController` 把 `/chat`、`/kb`、`/doc`、`/login`
  转发到 `index.html`，否则在这些页面按 F5 会 404。


## 十二、和微服务项目（shop-cloud）同时运行

两个项目的默认端口都想占 8080，同时跑需要错开一下。建议的端口划分：

| 用途 | kb-platform（单体） | shop-cloud（微服务） |
|---|---|---|
| Web 入口 | **8085** | 8080（网关） |
| MySQL | 本机 3306（库 `kb_platform`） | 本机 3306（库 `shop_*`） |
| Redis | **16380** | 16379 |
| PostgreSQL | 15432 | — |
| Nacos / RocketMQ / Seata | — | 18080 / 7876 / 8091 |

数据库可以共用一个本机 MySQL，两个项目用的是不同的库，互不影响。

```powershell
# 1. 起本项目的中间件（Redis 和 PostgreSQL，端口避开微服务项目）
$env:REDIS_PORT='16380'; $env:POSTGRES_PORT='15432'
docker compose up -d redis postgres

# 2. 换成 8085 启动
java -jar target/kb-platform-1.0.0.jar --server.port=8085

# 前端开发模式也要跟着改代理
cd ..\kb-web; $env:VITE_API_TARGET='http://localhost:8085'; pnpm dev
```

微服务那一侧照常（见 `shop-cloud/start-local.ps1`）。
根目录的 `start-all.ps1` 会把两边一起拉起来。

---

## 十三、AI 层：Spring AI + LangChain4j + RAG + MCP

> 类职责、调用链、面试讲法见 [`docs/03-SpringAI与MCP改造说明.md`](docs/03-SpringAI与MCP改造说明.md)

### 13.1 技术栈变化

| 能力 | 改造前 | 改造后 |
|---|---|---|
| 模型接入 | 手写 RestClient 调 OpenAI 兼容接口 | Spring AI `ChatClient` + `OpenAiChatModel` / `OllamaChatModel` |
| 多轮记忆 | 手工把历史拼进 Prompt | `ChatMemory` + `MessageChatMemoryAdvisor` |
| 记忆持久化 | 无 | Redis 热 + MySQL 冷 + Kryo 序列化 |
| 文档切分 | 手写 TextSplitter | LangChain4j `DocumentSplitters.recursive` |
| 工具调用 | 无 | `@Tool` + `ToolContext` |
| 对外能力 | 仅 HTTP | 额外提供 MCP Server（SSE / STDIO），2 个工具 |

### 13.2 环境与启动变化

- JDK 17、**Maven ≥ 3.6.3**（Spring Boot 3.4 的硬性要求，3.6.1 会在编译插件处直接报错）
- 接口文档从 knife4j 换成 springdoc：**`/doc.html` 已不存在，改用 `/swagger-ui.html`**
- **配置拆成两份**：`application.yml` 只放公共配置（MyBatis-Plus、检索参数、MCP 服务名等），
  连接串、密钥、模型地址统一放进 **`application-local.yml`**；默认 `spring.profiles.active=local`，
  换环境用 `--spring.profiles.active=prod` 覆盖（prod 文件按同样的键自己补一份）
- 新增环境变量（都有默认值，不配也能启动）：

| 变量 | 作用 | 默认 |
|---|---|---|
| `KB_AI_API_KEY` | 云端模型 Key，不配则走本地模拟实现 | 空 |
| `OLLAMA_BASE_URL` | 本地小模型地址 | `http://localhost:11434` |
| `KB_AI_LOCAL_ENABLED` | 是否启用本地模型分流 | `true` |

### 13.3 老库升级（重要）

`sql/init.sql` 只在 **MySQL 数据卷第一次创建**时执行。如果你已经有数据卷，
新增的 `ai_chat_memory` 表不会自动建，需要手工执行：

```sql
CREATE TABLE IF NOT EXISTS ai_chat_memory
(
    conversation_id VARCHAR(128) NOT NULL COMMENT '会话 ID（ChatMemory 的 memoryId）',
    payload         LONGBLOB     NOT NULL COMMENT 'Kryo 序列化的对话记忆',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (conversation_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='Spring AI 对话记忆';
```

不建也能跑：记忆写入失败会被降级处理（只丢记忆，不影响问答），但日志会有告警。

### 13.4 验证 MCP 是否生效

启动日志里出现这两行就说明注册成功：

```
McpServerAutoConfiguration : Registered tools: 2
Tomcat started on port 8080 (http)
```

用 curl 走一遍 SSE 握手（两个终端）：

```bash
# 终端 1：建立 SSE 连接，会打印 sessionId
curl -N -H "Accept: text/event-stream" http://localhost:8080/mcp/sse

# 终端 2：用上面的 sessionId 发 JSON-RPC
curl -X POST "http://localhost:8080/mcp/message?sessionId=<sessionId>" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"curl","version":"1.0"}}}'

curl -X POST "http://localhost:8080/mcp/message?sessionId=<sessionId>" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'

curl -X POST "http://localhost:8080/mcp/message?sessionId=<sessionId>" \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}'
```

`tools/list` 会返回 `searchKnowledgeBase` 和 `knowledgeBaseStats` 两个工具及其参数 Schema。

---

## 十四、本地全链路联调（无 API Key 也能测）

### 14.1 需要哪些中间件

| 组件 | 版本 | 本地端口 | 用途 | 是否必需 |
|---|---|---|---|---|
| MySQL | 8.0 | 3306 | 业务数据（用户 / 知识库 / 文档 / 会话 / 对话记忆冷存） | 必需 |
| Redis | 7 | 16380（容器） | 问答缓存 + 对话记忆热存 | 必需 |
| PostgreSQL + pgvector | pg16 / vector 0.8 | 15432（容器） | 向量库 | 必需 |
| Mock LLM（可选） | - | 9099 | 没有 API Key 时模拟 OpenAI 协议 | 联调用 |

```powershell
$env:REDIS_PORT='16380'; $env:POSTGRES_PORT='15432'
docker compose up -d redis postgres
```

### 14.2 环境变量

| 变量 | 说明 | 本地默认 |
|---|---|---|
| `MYSQL_HOST` / `MYSQL_PORT` / `MYSQL_USER` / `MYSQL_PASSWORD` | 主库连接 | localhost / 3306 / root / 123456 |
| `REDIS_PORT` / `REDIS_PASSWORD` | Redis | 6379 / redis123（容器请用 16380） |
| `POSTGRES_PORT` | pgvector | 5432（容器请用 15432） |
| `SPRING_AI_OPENAI_BASE_URL` | 云端模型地址 | 通义千问兼容地址 |
| `SPRING_AI_OPENAI_API_KEY` | 云端模型 Key | `sk-placeholder` |
| `KB_AI_API_KEY` | 业务侧判断是否走模拟实现 | 空（空则用 MockAiClient） |
| `KB_AI_LOCAL_ENABLED` | 是否启用 Ollama 分流 | true |

### 14.3 没有 Key 的联调方式

`tools/mock_llm_server.py` 起一个 OpenAI 兼容的假模型，这样 ChatClient、Advisor、流式、Embedding 都能真实跑到：

```powershell
python tools/mock_llm_server.py 9099

$env:SPRING_AI_OPENAI_BASE_URL='http://127.0.0.1:9099'
$env:SPRING_AI_OPENAI_API_KEY='sk-mock'
$env:KB_AI_API_KEY='sk-mock'
$env:KB_AI_LOCAL_ENABLED='false'
java -jar target/kb-platform-1.0.0.jar
```

> 注意：mock server 必须支持 **chunked 请求体**。Spring AI 的 RestClient 发 embeddings 时不带
> `Content-Length`，只按 `Content-Length` 读会拿到空 body，模型侧返回零向量，
> 最终表现为「检索分数是 NaN」。

### 14.4 前端怎么启动

开发模式（推荐，改代码热更新）：

```powershell
cd kb-web
pnpm install        # 首次
pnpm dev            # http://localhost:5173
```

Vite 已配好 `/api` 代理到 `http://localhost:8080`，后端换端口时用：

```powershell
$env:VITE_API_TARGET='http://localhost:8085'; pnpm dev
```

生产模式（前后端一起打成一个 jar）：

```powershell
cd kb-web
pnpm build          # 输出到 ../kb-platform/src/main/resources/static
cd ..\kb-platform
mvn -DskipTests package
java -jar target/kb-platform-1.0.0.jar
# 访问 http://localhost:8080/
```
