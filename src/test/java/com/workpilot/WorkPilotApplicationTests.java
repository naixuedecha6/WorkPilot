package com.workpilot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 上下文加载冒烟测试:验证整个 Spring 容器能正常装配。
 * 跑通这一条,基本就证明所有 Bean 之间的依赖没有问题。
 */
@SpringBootTest
class WorkPilotApplicationTests {

    @Test
    void contextLoads() {
    }
}
