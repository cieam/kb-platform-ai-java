package com.example.kb.vector;

import lombok.Data;

/**
 * 向量库里的一个切片。score 只在检索结果里有值。
 */
@Data
public class ChunkVector {

    private Long id;
    private Long kbId;
    private Long docId;
    private Integer chunkNo;
    private String content;
    private Double score;
}
