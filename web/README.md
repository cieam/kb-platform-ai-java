# web · 前端工程

> 已并入 `kb-platform` 仓库，本目录就是前端的全部源码。
> 在仓库根目录启动：`mvn spring-boot:run`；本目录启动：`pnpm dev` → <http://localhost:5173>

AI 知识库问答平台的前端，Vue 3 + Vite + Element Plus。

## 一、技术栈

| 用途 | 选型 |
|---|---|
| 框架 | Vue 3（组合式 API + `<script setup>`） |
| 构建 | Vite 5 |
| UI 组件 | Element Plus 2（中文语言包，图标全量注册） |
| 路由 | Vue Router 4（history 模式） |
| 状态 | Pinia |
| 请求 | Axios（统一拦截器 + token 注入） |
| 流式 | fetch + ReadableStream（**不用 EventSource**，它发不了 POST body） |
| Markdown | markdown-it（`html: false`，不执行模型输出里的标签） |

## 二、目录结构

```
web
├── index.html              Vite 入口
├── vite.config.js          别名 @ → src；dev 代理 /api → 8080；build 输出到后端 static
└── src
    ├── main.js             注册 Element Plus、图标、Pinia、路由
    ├── App.vue             只有 <router-view />
    ├── styles/main.css     全局样式 + Markdown 渲染样式 + 流式光标
    ├── router/index.js     路由表 + 登录守卫
    ├── store
    │   ├── user.js         token、昵称，落 localStorage
    │   └── kb.js           知识库列表与「当前知识库」，全站共享
    ├── api
    │   ├── request.js      axios 实例：注入 token、剥统一返回体、401 跳登录
    │   ├── auth.js / kb.js / doc.js
    │   └── chat.js         含 SSE 解析（streamAsk）
    ├── layout/AppLayout.vue   侧边菜单 + 顶栏知识库切换器
    ├── components/MessageItem.vue  消息气泡：Markdown、引用来源、复制
    └── views
        ├── LoginView.vue   登录 / 注册
        ├── KbView.vue      知识库增删改查 + 分页
        ├── DocView.vue     拖拽上传、解析状态轮询、重新解析、删除
        └── ChatView.vue    会话列表 + 流式问答 + 引用展示
```

## 三、开发

```bash
pnpm install
pnpm dev          # http://localhost:5173，/api 自动代理到后端 8080
```

后端要先起来：`cd .. && mvn spring-boot:run`

## 四、构建与部署

```bash
pnpm build
```

产物直接输出到 `../src/main/resources/static`（在 `vite.config.js` 里配置），
所以**打完前端再打后端 jar，一个 jar 就是完整应用**：

```bash
cd .. && mvn clean package -DskipTests
java -jar target/kb-platform-1.0.0.jar
```

浏览器打开 <http://localhost:8080/> 即可。

> 为什么要构建进后端 static：这是单体项目最省事的部署方式，不用 Nginx、不用配 CORS。
> 如果以后要前后端分开部署，把 `build.outDir` 改回 `dist`，再用 Nginx 托管并反代 `/api` 即可。

## 五、几个实现细节

1. **路由用 history 模式**，刷新 `/chat`、`/kb` 这类地址需要服务端回落到 `index.html`。
   后端有一个 `SpaForwardController` 专门做这件事，同时这几个路径要加进 Security 白名单。
2. **流式渲染节流**：每 60ms 把攒下的增量写入 DOM，否则每个 token 都重跑一次 Markdown 渲染，
   长回答会明显卡顿。
3. **上传后自动轮询**：文档列表里只要还有 `PENDING`/`PARSING`，就每 2 秒刷新一次；
   全部结束后自动停掉，不留无用定时器。
4. **引用来源**：`references` 事件给出 `{fileName, chunkNo, score, content}`，
   鼠标悬停在「相关度」上能看到对应的原文片段——这就是「答案可溯源」的落点。
5. **停止生成**：用 `AbortController` 中断 fetch，后端 `SseEmitter.send` 抛异常后中断本次生成。
