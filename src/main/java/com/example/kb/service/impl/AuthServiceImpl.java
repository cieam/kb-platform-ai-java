package com.example.kb.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.kb.common.exception.BizException;
import com.example.kb.common.result.ResultCode;
import com.example.kb.common.util.JwtUtil;
import com.example.kb.common.util.UserContext;
import com.example.kb.domain.dto.LoginRequest;
import com.example.kb.domain.dto.RegisterRequest;
import com.example.kb.domain.entity.User;
import com.example.kb.domain.vo.LoginVO;
import com.example.kb.mapper.UserMapper;
import com.example.kb.service.AuthService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public LoginVO register(RegisterRequest request) {
        Long exists = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "用户名已存在");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        String nickname = request.getNickname();
        user.setNickname(nickname == null || nickname.isBlank() ? request.getUsername() : nickname);
        user.setRole("USER");
        userMapper.insert(user);
        return buildLoginVO(user);
    }

    @Override
    public LoginVO login(LoginRequest request) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        // 用户不存在和密码错误返回同一句提示，避免被撞库
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BizException(ResultCode.PARAM_ERROR, "用户名或密码错误");
        }
        return buildLoginVO(user);
    }

    @Override
    public User currentUser() {
        User user = userMapper.selectById(UserContext.getUserId());
        if (user == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    private LoginVO buildLoginVO(User user) {
        String token = jwtUtil.createToken(user.getId(), user.getUsername());
        return new LoginVO(token, user.getId(), user.getUsername(), user.getNickname());
    }
}
