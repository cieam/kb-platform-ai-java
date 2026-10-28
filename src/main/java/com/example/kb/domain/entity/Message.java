package com.example.kb.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 消息表只追加、不改不删，所以没有逻辑删除字段。
 * 列名用 msg_role：role 在 MySQL 8 里是关键字，能避开就避开。
 */
@Data
@TableName("kb_message")
public class Message {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ASSISTANT = "ASSISTANT";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    private String msgRole;

    private String content;

    /** 引用的切片，JSON 字符串，用于前端回显出处 */
    private String refChunks;

    private Integer promptTokens = 0;

    private Integer completionTokens = 0;

    private Long costMs = 0L;

    private LocalDateTime createTime;
}
