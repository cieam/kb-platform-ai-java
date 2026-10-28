package com.example.kb.common.util;

import com.example.kb.common.exception.BizException;
import com.example.kb.common.result.ResultCode;

/**
 * 当前登录用户，由 JwtAuthenticationFilter 写入、请求结束时清理。
 * 注意：必须在 finally 里 clear，否则线程池复用会造成串号。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USERNAME = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId, String username) {
        USER_ID.set(userId);
        USERNAME.set(username);
    }

    public static Long getUserId() {
        Long userId = USER_ID.get();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    public static String getUsername() {
        return USERNAME.get();
    }

    /**
     * 把任务包成「带着当前登录用户一起跑」。
     *
     * <p>为什么必须有这个方法：ThreadLocal 只对当前线程有效，
     * 一旦把业务丢进线程池（SSE 推流、文档解析都属于这种），
     * 新线程里 UserContext 是空的，业务代码调 {@link #getUserId()} 会直接抛 401。
     * 这是本项目真实踩过的坑：非流式问答正常、流式问答 401。
     *
     * <p>方法在提交任务时就把身份取出来捕获住，执行完再 clear，
     * 避免线程池复用导致用户串号。
     */
    public static Runnable wrap(Runnable task) {
        Long userId = USER_ID.get();
        String username = USERNAME.get();
        return () -> {
            set(userId, username);
            try {
                task.run();
            } finally {
                clear();
            }
        };
    }

    public static void clear() {
        USER_ID.remove();
        USERNAME.remove();
    }
}
