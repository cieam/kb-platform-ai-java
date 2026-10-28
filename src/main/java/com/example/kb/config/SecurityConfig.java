package com.example.kb.config;

import jakarta.servlet.DispatcherType;
import com.example.kb.controller.SpaForwardController;
import com.example.kb.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;

/**
 * 无状态 JWT 鉴权：关掉 CSRF 和 Session，登录接口、前端页面与接口文档放行，其余全部要 token。
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * 放行清单：登录注册、前端页面与前端路由、接口文档、错误页。
     * 加接口时别忘了同步这里。
     */
    private static final String[] WHITELIST = buildWhitelist();

    private static String[] buildWhitelist() {
        List<String> paths = new ArrayList<>(List.of(
                "/api/auth/**",
                "/", "/index.html", "/favicon.ico",
                "/assets/**", "/css/**", "/js/**", "/img/**",
                "/doc.html", "/webjars/**", "/swagger-ui/**", "/swagger-ui.html",
                "/v3/api-docs/**", "/error",
                // MCP Server：外部 AI 客户端（IDE 插件、其他 Agent）不走 JWT，
                // 用 MCP 自己的握手流程；工具内部按 kbId 做数据校验
                "/mcp/**"
        ));
        // 前端 history 路由要能直接打开，否则刷新就 404
        paths.addAll(List.of(SpaForwardController.routes()));
        return paths.toArray(new String[0]);
    }

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // SSE（SseEmitter）结束时会再走一次 ASYNC 派发，此时 SecurityContext
                        // 已经不在请求线程上，如果不放行就会抛 Access Denied，
                        // 响应头早已发出，浏览器表现为「网络中断」——必须显式放行。
                        .dispatcherTypeMatchers(DispatcherType.ASYNC, DispatcherType.ERROR).permitAll()
                        .requestMatchers(WHITELIST).permitAll()
                        .anyRequest().authenticated())
                // 未登录时也返回统一返回体，前端才好统一处理
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(200);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"code\":401,\"msg\":\"未登录或登录已过期\",\"data\":null}");
                }))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
