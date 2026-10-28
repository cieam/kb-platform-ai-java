package com.example.kb.common.util;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归字符切分。
 * 先用空行切段落，段落内再按句号/换行切句，最后按 size 硬切；
 * 输出时为相邻两段加上 overlap 长度的重叠，防止答案正好落在切口上。
 */
public final class TextSplitter {

    private final int size;
    private final int overlap;

    public TextSplitter(int size, int overlap) {
        this.size = size <= 0 ? 800 : size;
        this.overlap = Math.max(0, Math.min(overlap, this.size / 2));
    }

    public List<String> split(String raw) {
        List<String> rawChunks = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return rawChunks;
        }
        String text = raw.replace("\r\n", "\n").replaceAll("\n{3,}", "\n\n").strip();

        List<String> units = new ArrayList<>();
        for (String paragraph : text.split("\n\n")) {
            String p = paragraph.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() <= size) {
                units.add(p);
            } else {
                units.addAll(splitLongParagraph(p));
            }
        }

        StringBuilder current = new StringBuilder();
        for (String unit : units) {
            if (current.length() > 0 && current.length() + 1 + unit.length() > size) {
                rawChunks.add(current.toString());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append('\n');
            }
            current.append(unit);
        }
        if (current.length() > 0) {
            rawChunks.add(current.toString());
        }

        List<String> result = new ArrayList<>(rawChunks.size());
        for (int i = 0; i < rawChunks.size(); i++) {
            if (i == 0) {
                result.add(rawChunks.get(0));
                continue;
            }
            String tail = tail(rawChunks.get(i - 1));
            result.add(tail.isEmpty() ? rawChunks.get(i) : tail + "\n" + rawChunks.get(i));
        }
        return result;
    }

    private List<String> splitLongParagraph(String paragraph) {
        List<String> sentences = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < paragraph.length(); i++) {
            char c = paragraph.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '；' || c == '\n') {
                sentences.add(paragraph.substring(start, i + 1));
                start = i + 1;
            }
        }
        if (start < paragraph.length()) {
            sentences.add(paragraph.substring(start));
        }

        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String sentence : sentences) {
            if (sentence.length() > size) {
                if (current.length() > 0) {
                    out.add(current.toString());
                    current.setLength(0);
                }
                for (int i = 0; i < sentence.length(); i += size) {
                    out.add(sentence.substring(i, Math.min(sentence.length(), i + size)));
                }
                continue;
            }
            if (current.length() > 0 && current.length() + sentence.length() > size) {
                out.add(current.toString());
                current.setLength(0);
            }
            current.append(sentence);
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
        return out;
    }

    private String tail(String text) {
        if (overlap <= 0) {
            return "";
        }
        return text.length() <= overlap ? text : text.substring(text.length() - overlap);
    }
}
