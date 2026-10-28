package com.example.kb.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 答案引用的切片，前端据此渲染「出处」。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceVO {

    private Long docId;
    private String fileName;
    private Integer chunkNo;
    private Double score;
    private String content;
}
