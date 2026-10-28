package com.example.kb.domain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank(message = "不能为空")
    private String username;

    @NotBlank(message = "不能为空")
    private String password;
}
