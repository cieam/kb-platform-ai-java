package com.example.kb.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前端路由用 history 模式，直接访问 /chat、/kb、/doc 或在这些页面按 F5 时，
 * 浏览器会向服务端要这些路径。如果没有回落，Spring Boot 会返回 404。
 *
 * <p>这里把它们统一转发到 index.html，交给前端路由处理；
 * js/css 等静态资源仍由 Spring Boot 的静态资源处理器提供。
 */
@Controller
public class SpaForwardController {

    private static final String[] SPA_ROUTES = {"/chat", "/kb", "/doc", "/login"};

    @GetMapping({"/chat", "/kb", "/doc", "/login"})
    public String forward() {
        return "forward:/index.html";
    }

    /** 暴露给文档和测试用，避免魔法值散落各处 */
    public static String[] routes() {
        return SPA_ROUTES.clone();
    }
}
