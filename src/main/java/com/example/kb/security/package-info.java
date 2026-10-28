/**
 * Web 安全层。
 *
 * <p>JwtAuthenticationFilter 从 Authorization 头解析用户并写入上下文，解析失败不抛异常，交给授权规则决定是否放行。
 */
package com.example.kb.security;
