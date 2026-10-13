package com.workpilot.service;

import com.workpilot.entity.User;
import com.workpilot.exception.AgentException;
import com.workpilot.repository.UserRepository;
import com.workpilot.security.AppPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * 提供"当前登录用户"上下文。
 * 数据来源:Spring Security 的 SecurityContext(由 JwtAuthenticationFilter 写入)。
 */
@Service
@RequiredArgsConstructor
public class UserContextService {

    private final UserRepository userRepository;

    /** 当前登录用户 ID。未登录时抛 401(由全局异常处理统一返回)。 */
    public Long currentUserId() {
        AppPrincipal p = currentPrincipal();
        if (p == null) {
            throw new AgentException("UNAUTHORIZED", "未登录");
        }
        return p.getUserId();
    }

    public AppPrincipal currentPrincipal() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof AppPrincipal p)) {
            return null;
        }
        return p;
    }

    public String getUsername(Long userId) {
        return userRepository.findById(userId)
                .map(User::getUsername)
                .orElseThrow(() -> new AgentException("USER_NOT_FOUND", "用户不存在: " + userId));
    }
}
