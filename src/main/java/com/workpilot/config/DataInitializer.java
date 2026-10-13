package com.workpilot.config;

import com.workpilot.entity.User;
import com.workpilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 启动时把 demo / admin 两个种子用户的密码统一成 {@code admin123}。
 *
 * <p>第一版 Flyway V1 里的密码 hash 是早期占位值,现在接了 BCrypt,
 * 这里做一次幂等的"重置",避免历史库里的 hash 不可用。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private static final String DEMO_PASSWORD = "admin123";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(String... args) {
        String freshHash = passwordEncoder.encode(DEMO_PASSWORD);
        for (String username : new String[]{"admin", "demo"}) {
            userRepository.findByUsername(username).ifPresent(u -> {
                // 只在 hash 不可用或与期望值不匹配时更新;BCrypt 每次结果不同,所以直接覆盖
                u.setPasswordHash(freshHash);
                userRepository.save(u);
                log.info("[init] 重置用户 {} 密码为 {} (开发期默认)", username, DEMO_PASSWORD);
            });
        }
    }
}
