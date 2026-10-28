package com.example.kb.controller;

import com.example.kb.common.result.R;
import com.example.kb.domain.dto.LoginRequest;
import com.example.kb.domain.dto.RegisterRequest;
import com.example.kb.domain.entity.User;
import com.example.kb.domain.vo.LoginVO;
import com.example.kb.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "01 认证")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    public R<LoginVO> register(@RequestBody @Valid RegisterRequest request) {
        return R.ok(authService.register(request));
    }

    @Operation(summary = "登录，返回 JWT")
    @PostMapping("/login")
    public R<LoginVO> login(@RequestBody @Valid LoginRequest request) {
        return R.ok(authService.login(request));
    }

    @Operation(summary = "当前登录用户")
    @GetMapping("/me")
    public R<User> me() {
        return R.ok(authService.currentUser());
    }
}
