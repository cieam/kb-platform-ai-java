package com.example.kb.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_document")
public class KbDocument {

    /** 文档状态机：PENDING -> PARSING -> SUCCESS / FAILED */
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PARSING = "PARSING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;

    private String fileName;

    private String fileType;

    private Long fileSize = 0L;

    /** 本地存储路径，换 MinIO 后改成对象 key */
    private String filePath;

    private String status = STATUS_PENDING;

    private Integer chunkCount = 0;

    private String errorMsg;

    private Long parseCostMs = 0L;

    @TableLogic
    @JsonIgnore
    private Integer deleted = 0;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
