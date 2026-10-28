package com.example.kb.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatRequest {

    @NotNull(message = "不能为空")
    private Long kbId;

    /** 不传则自动新建一个会话 */
    private Long conversationId;

    @NotBlank(message = "不能为空")
    @Size(max = 500, message = "不能超过 500 个字符")
    private String question;
}
