package com.example.kb.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_knowledge_base")
public class KnowledgeBase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    /** 归属用户，所有查询都必须带上这个条件做隔离 */
    private Long ownerId;

    /** PRIVATE：仅自己可见；TEAM：团队可见 */
    private String scope = "PRIVATE";

    private Integer docCount = 0;

    /** 每个知识库可以有自己的切分参数 */
    private Integer chunkSize = 800;

    private Integer chunkOverlap = 150;

    @TableLogic
    @JsonIgnore
    private Integer deleted = 0;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
