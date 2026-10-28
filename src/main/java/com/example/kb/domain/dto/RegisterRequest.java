package com.example.kb.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "不能为空")
    @Size(min = 3, max = 32, message = "长度需在 3 到 32 之间")
    private String username;

    @NotBlank(message = "不能为空")
    @Size(min = 6, max = 32, message = "长度需在 6 到 32 之间")
    private String password;

    private String nickname;
}
