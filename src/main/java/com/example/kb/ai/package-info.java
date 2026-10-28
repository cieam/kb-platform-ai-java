/**
 * 大模型接入。
 *
 * <p>AiClient 是抽象，业务层只依赖它；OpenAiCompatibleAiClient 是默认实现，负责分批向量化、退避重试、流式对话，以及未配置 Key 时的本地降级。
 */
package com.example.kb.ai;
