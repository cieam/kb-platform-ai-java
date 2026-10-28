package com.example.kb.retrieval;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class KeywordExtractorTest {

    private final KeywordExtractor extractor = new KeywordExtractor();

    @Test
    void extractsChineseBigrams() {
        List<String> keywords = extractor.extract("请假流程是什么");
        assertTrue(keywords.contains("请假"));
        assertTrue(keywords.contains("流程"));
    }

    @Test
    void keepsLatinTokensForModelNumbers() {
        List<String> keywords = extractor.extract("XL-2000 型号的保修期是多久");
        assertTrue(keywords.contains("xl"), "型号类关键词必须被保留：" + keywords);
    }

    @Test
    void keywordCountIsCapped() {
        List<String> keywords = extractor.extract("这是一个非常非常长的中文问题用来验证关键词数量上限");
        assertTrue(keywords.size() <= 8, "关键词数量应当有上限，实际 " + keywords.size());
    }

    @Test
    void blankInputReturnsEmpty() {
        assertTrue(extractor.extract(null).isEmpty());
        assertTrue(extractor.extract("   ").isEmpty());
    }
}
