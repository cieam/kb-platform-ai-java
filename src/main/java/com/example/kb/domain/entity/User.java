package com.example.kb.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("kb_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** 只存 BCrypt 密文，且永不返回给前端 */
    @JsonIgnore
    private String password;

    private String nickname;

    private String role;

    @TableLogic
    @JsonIgnore
    private Integer deleted = 0;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
