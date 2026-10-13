package com.workpilot.service;

import com.workpilot.dto.AuthDtos;
import com.workpilot.entity.User;
import com.workpilot.entity.UserRole;
import com.workpilot.exception.AgentException;
import com.workpilot.repository.UserRepository;
import com.workpilot.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;

    @Transactional
    public AuthDtos.AuthResponse register(AuthDtos.RegisterRequest req) {
        userRepository.findByUsername(req.username()).ifPresent(u -> {
            throw new AgentException("USERNAME_TAKEN", "用户名已存在: " + req.username());
        });
        User u = User.builder()
                .username(req.username())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(UserRole.USER)
                .build();
        u = userRepository.save(u);
        return build(u);
    }

    @Transactional(readOnly = true)
    public AuthDtos.AuthResponse login(AuthDtos.LoginRequest req) {
        User u = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new AgentException("INVALID_CREDENTIALS", "用户名或密码错误"));
        if (!passwordEncoder.matches(req.password(), u.getPasswordHash())) {
            throw new AgentException("INVALID_CREDENTIALS", "用户名或密码错误");
        }
        return build(u);
    }

    @Transactional(readOnly = true)
    public AuthDtos.UserInfo me(User u) {
        return new AuthDtos.UserInfo(u.getId(), u.getUsername(), u.getRole().name(),
                u.getCreatedAt() == null ? null : u.getCreatedAt().toString());
    }

    private AuthDtos.AuthResponse build(User u) {
        String token = tokenProvider.createToken(u.getId(), u.getUsername(), u.getRole().name());
        return new AuthDtos.AuthResponse(token, tokenProvider.getExpirationSeconds(), me(u));
    }
}
