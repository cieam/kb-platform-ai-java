package com.example.kb.retrieval;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 从问题里抽取关键词，供关键词召回使用。
 *
 * <p>中文没有天然空格，这里用 2-gram（相邻两字）切分：
 * 「请假流程是什么」→ 请假 / 假流 / 流程 / 程是 / 是什 / 什么。
 * 好处是不依赖任何分词器；代价是会引入「程是」这种无意义片段，
 * 但关键词召回只用它做粗筛，后面还有 RRF 融合，可以接受。
 *
 * <p>生产环境应换成带中文分词的检索（PostgreSQL 的 zhparser、IK，或直接把检索交给 ES）。
 */
@Component
public class KeywordExtractor {

    /** 关键词上限，避免问题很长时拼出几十个 OR 条件 */
    private static final int MAX_KEYWORDS = 8;
    private static final int MIN_CJK_RUN = 2;

    public List<String> extract(String question) {
        Set<String> keywords = new LinkedHashSet<>();
        if (question == null || question.isBlank()) {
            return new ArrayList<>();
        }
        String text = question.trim();

        // 1. 拉丁字母与数字：整词保留，型号、编号类问题靠它
        for (String token : text.split("[^0-9A-Za-z]+")) {
            if (token.length() >= 2) {
                keywords.add(token.toLowerCase());
            }
            if (keywords.size() >= MAX_KEYWORDS) {
                return new ArrayList<>(keywords);
            }
        }

        // 2. 连续汉字：按 2-gram 切
        StringBuilder run = new StringBuilder();
        for (int i = 0; i <= text.length(); i++) {
            char c = i < text.length() ? text.charAt(i) : ' ';
            if (isCjk(c)) {
                run.append(c);
                continue;
            }
            if (run.length() >= MIN_CJK_RUN) {
                String segment = run.toString();
                for (int j = 0; j + 2 <= segment.length(); j++) {
                    keywords.add(segment.substring(j, j + 2));
                    if (keywords.size() >= MAX_KEYWORDS) {
                        return new ArrayList<>(keywords);
                    }
                }
            }
            run.setLength(0);
        }
        return new ArrayList<>(keywords);
    }

    private static boolean isCjk(char c) {
        return c >= 0x4E00 && c <= 0x9FFF;
    }
}
