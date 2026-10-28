package com.example.kb.domain.vo;

import com.example.kb.domain.entity.KnowledgeBase;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KbVO {

    private Long id;
    private String name;
    private String description;
    private String scope;
    private Integer docCount;
    private Integer chunkSize;
    private Integer chunkOverlap;
    private LocalDateTime createTime;

    public static KbVO from(KnowledgeBase kb) {
        KbVO vo = new KbVO();
        vo.setId(kb.getId());
        vo.setName(kb.getName());
        vo.setDescription(kb.getDescription());
        vo.setScope(kb.getScope());
        vo.setDocCount(kb.getDocCount());
        vo.setChunkSize(kb.getChunkSize());
        vo.setChunkOverlap(kb.getChunkOverlap());
        vo.setCreateTime(kb.getCreateTime());
        return vo;
    }
}
