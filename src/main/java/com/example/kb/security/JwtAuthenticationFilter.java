package com.example.kb.security;

import com.example.kb.common.util.JwtUtil;
import com.example.kb.common.util.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * 从 Authorization: Bearer xxx 解析出用户，写进 UserContext 与 SecurityContext。
 * 解析失败不抛异常，交给后面的授权规则决定是否放行。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader(HEADER);
        if (header != null && header.startsWith(PREFIX)) {
            try {
                Claims claims = jwtUtil.parse(header.substring(PREFIX.length()));
                Long userId = jwtUtil.parseUserId(claims);
                String username = jwtUtil.parseUsername(claims);
                if (userId != null) {
                    UserContext.set(userId, username);
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(username, null, Collections.emptyList()));
                }
            } catch (Exception e) {
                // token 过期或伪造：清空上下文，让后续的认证入口返回 401
                UserContext.clear();
                SecurityContextHolder.clearContext();
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            UserContext.clear();
        }
    }
}
