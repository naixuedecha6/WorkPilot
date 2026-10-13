package com.workpilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @NotBlank String username,
            @NotBlank String password) {}

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 32)
            @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "用户名仅允许字母数字下划线点连字符")
            String username,
            @NotBlank @Size(min = 6, max = 64) String password) {}

    public record AuthResponse(
            String token,
            long expiresIn,
            UserInfo user) {}

    public record UserInfo(
            Long id,
            String username,
            String role,
            String createdAt) {}
}
