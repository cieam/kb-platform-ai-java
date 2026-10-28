package com.example.kb.service;

import com.example.kb.domain.dto.LoginRequest;
import com.example.kb.domain.dto.RegisterRequest;
import com.example.kb.domain.entity.User;
import com.example.kb.domain.vo.LoginVO;

public interface AuthService {

    LoginVO register(RegisterRequest request);

    LoginVO login(LoginRequest request);

    /** 取当前登录用户，用于 /api/auth/me */
    User currentUser();
}
