package com.workpilot.security;

import com.workpilot.entity.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 当前登录用户的安全上下文。挂在 {@link org.springframework.security.core.Authentication#getPrincipal()}。
 */
@Getter
@RequiredArgsConstructor
public class AppPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final String role;

    public static AppPrincipal from(User u) {
        return new AppPrincipal(u.getId(), u.getUsername(), u.getRole().name());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override public String getPassword() { return null; }   // 不在 SecurityContext 里持有明文
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
