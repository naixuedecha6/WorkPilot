package com.workpilot.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * 签发 / 解析 JWT。
 *
 * <p>默认 HS256,密钥从配置 {@code workpilot.jwt.secret} 读,有效期 8 小时。
 * 生产必须把密钥放到环境变量,不要写在配置里。</p>
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    @Value("${workpilot.jwt.secret:dev-only-secret-please-change-in-production-32bytes-min}")
    private String secret;

    @Value("${workpilot.jwt.expiration-hours:8}")
    private long expirationHours;

    @Value("${workpilot.jwt.issuer:workpilot}")
    private String issuer;

    private SecretKey key;

    @PostConstruct
    void init() {
        // jjwt 0.12 要求 HMAC-SHA 密钥至少 256 bit
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // 自动补到 32 字节,避免开发期密钥太短报错
            byte[] padded = new byte[32];
            System.arraycopy(bytes, 0, padded, 0, bytes.length);
            bytes = padded;
        }
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public String createToken(Long userId, String username, String role) {
        Instant now = Instant.now();
        Instant exp = now.plus(Duration.ofHours(expirationHours));
        return Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /** 解析失败抛 {@link JwtException};调用方决定如何处理(返回 401 或忽略)。 */
    public Claims parse(String token) {
        Jws<Claims> jws = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token);
        return jws.getPayload();
    }

    public long getExpirationSeconds() {
        return Duration.ofHours(expirationHours).toSeconds();
    }
}
