/**
 * 检索策略。
 *
 * <p>关键词抽取 + RRF 融合 + 混合检索入口。这一层不碰数据库连接细节，只负责「召回哪些片段、怎么排序」。
 */
package com.example.kb.retrieval;
