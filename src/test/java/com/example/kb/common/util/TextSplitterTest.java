package com.example.kb.common.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 切分器是整条流水线里最容易写错、又最难肉眼验证的一环，所以必须单测。
 */
class TextSplitterTest {

    private final TextSplitter splitter = new TextSplitter(50, 10);

    @Test
    void blankInputReturnsEmptyList() {
        assertTrue(splitter.split(null).isEmpty());
        assertTrue(splitter.split("   ").isEmpty());
    }

    @Test
    void longTextIsSplitIntoMultipleChunks() {
        List<String> chunks = splitter.split("第一段内容。".repeat(60));
        assertTrue(chunks.size() > 1, "超过 chunk_size 的文本必须被切开");
    }

    @Test
    void neighbourChunksOverlap() {
        List<String> chunks = splitter.split(
                ("段落一内容内容内容内容。\n\n段落二内容内容内容内容。\n\n段落三内容内容内容内容。").repeat(5));
        assertTrue(chunks.size() >= 2);

        String previous = chunks.get(0);
        String tail = previous.substring(previous.length() - 10);
        assertTrue(chunks.get(1).startsWith(tail), "相邻两段之间应当有 overlap 长度的重叠");
    }

    @Test
    void contentIsNotLost() {
        List<String> chunks = splitter.split("啊".repeat(300));
        int total = chunks.stream().mapToInt(c -> c.replace("\n", "").length()).sum();
        assertTrue(total > 0);
        assertTrue(chunks.stream().allMatch(c -> c.replace("\n", "").matches("啊+")));
    }

    @Test
    void overlapIsCappedToHalfOfSize() {
        // overlap 传 500，远大于 size 的一半，构造器应当把它压到 50
        TextSplitter safe = new TextSplitter(100, 500);
        List<String> chunks = safe.split("测试内容。".repeat(100));
        assertTrue(chunks.size() > 1);
        int overlapLength = chunks.get(1).indexOf('\n');
        assertTrue(overlapLength > 0 && overlapLength <= 50,
                "重叠长度应当被限制在 size 的一半以内，实际 " + overlapLength);
    }
}
