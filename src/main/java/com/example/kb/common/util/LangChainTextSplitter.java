package com.example.kb.common.util;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文档切分：交给 LangChain4j 的递归切分器。
 *
 * <p>为什么换掉手写版本？LangChain4j 的 recursive splitter 会按
 * 「段落 → 换行 → 句子 → 词」的优先级逐层尝试，尽量在语义边界处断开；
 * 手写版本只有「空行 → 句号 → 硬切」两级，遇到长表格或没有标点的条款容易切碎。
 *
 * <p>chunkSize / overlap 仍然由知识库配置决定，不写死——
 * 不同文档（制度条款 vs 设备手册）合适的粒度本来就不一样。
 */
@Component
public class LangChainTextSplitter {

    private static final int DEFAULT_SIZE = 800;

    public List<String> split(String raw, int size, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return chunks;
        }
        String text = raw.replace("\r\n", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .strip();

        int maxSize = size <= 0 ? DEFAULT_SIZE : size;
        int maxOverlap = Math.max(0, Math.min(overlap, maxSize / 2));

        DocumentSplitter splitter = DocumentSplitters.recursive(maxSize, maxOverlap);
        List<TextSegment> segments = splitter.split(Document.from(text));
        for (TextSegment segment : segments) {
            String content = segment.text();
            if (content != null && !content.isBlank()) {
                chunks.add(content.strip());
            }
        }
        return chunks;
    }
}
