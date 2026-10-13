package com.workpilot.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 每次请求只走一次的 JWT 过滤器:
 * <ol>
 *   <li>从 {@code Authorization: Bearer xxx} 头取 token</li>
 *   <li>解析成功 → 在 SecurityContext 放 {@link AppPrincipal}</li>
 *   <li>解析失败 / 无 token → 放行(让后续的授权规则决定 401/403)</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String HEADER = "Authorization";
    private static final String PREFIX = "Bearer ";

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = extract(request);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                Claims c = tokenProvider.parse(token);
                Long userId = Long.valueOf(c.getSubject());
                String username = c.get("username", String.class);
                String role = c.get("role", String.class);
                AppPrincipal principal = new AppPrincipal(userId, username, role);
                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (JwtException | IllegalArgumentException e) {
                log.debug("无效 JWT: {}", e.getMessage());
                // 不放 context,后续 SecurityFilterChain 会按未认证处理
            }
        }
        chain.doFilter(request, response);
    }

    private String extract(HttpServletRequest request) {
        String h = request.getHeader(HEADER);
        if (h != null && h.startsWith(PREFIX)) {
            return h.substring(PREFIX.length()).trim();
        }
        return null;
    }
}
