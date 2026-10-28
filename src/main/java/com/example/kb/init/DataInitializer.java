package com.example.kb.init;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.kb.domain.entity.User;
import com.example.kb.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动时建一个演示账号，避免每次都手动注册。
 * 密码在运行时用 BCrypt 加密，所以 SQL 脚本里不需要写死密文。
 *
 * <p>想接一个已有的库、不希望插入种子数据时，用 {@code --kb.init.enabled=false} 关掉它。
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "kb.init", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer implements ApplicationRunner {

    private static final String DEFAULT_USERNAME = "admin";
    private static final String DEFAULT_PASSWORD = "123456";

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, DEFAULT_USERNAME));
        if (count != null && count > 0) {
            return;
        }
        User user = new User();
        user.setUsername(DEFAULT_USERNAME);
        user.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        user.setNickname("管理员");
        user.setRole("ADMIN");
        userMapper.insert(user);
        log.info("已初始化演示账号：{} / {}", DEFAULT_USERNAME, DEFAULT_PASSWORD);
    }
}
