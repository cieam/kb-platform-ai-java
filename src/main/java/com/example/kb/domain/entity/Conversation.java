package com.example.kb.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_conversation")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long kbId;

    private Long userId;

    private String title;

    private Integer messageCount = 0;

    private LocalDateTime lastMessageTime;

    @TableLogic
    @JsonIgnore
    private Integer deleted = 0;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
