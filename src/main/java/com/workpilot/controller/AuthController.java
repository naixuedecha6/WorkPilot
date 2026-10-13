package com.workpilot.controller;

import com.workpilot.dto.AuthDtos;
import com.workpilot.entity.User;
import com.workpilot.repository.UserRepository;
import com.workpilot.security.AppPrincipal;
import com.workpilot.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<AuthDtos.AuthResponse> login(@Valid @RequestBody AuthDtos.LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthDtos.AuthResponse> register(@Valid @RequestBody AuthDtos.RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }

    @GetMapping("/me")
    public ResponseEntity<AuthDtos.UserInfo> me(@AuthenticationPrincipal AppPrincipal principal) {
        User u = userRepository.findById(principal.getUserId())
                .orElseThrow(() -> new IllegalStateException("登录态异常:用户不存在"));
        return ResponseEntity.ok(authService.me(u));
    }
}
