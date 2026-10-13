package com.workpilot.agent;

import com.workpilot.dto.AgentPlan;
import com.workpilot.dto.AgentStep;
import com.workpilot.dto.ToolContext;
import com.workpilot.dto.ToolResult;
import com.workpilot.exception.AgentException;
import com.workpilot.tools.Tool;
import com.workpilot.tools.ToolRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AgentPolicy 单元测试:验证计划校验、工具白名单、参数上限。
 * 用真实 Tool 实现代替 mock,避免 Mockito stub 默认方法的限制。
 */
class AgentPolicyTest {

    static class FakeTool implements Tool {
        private final String name;
        private final boolean approval;
        FakeTool(String name, boolean approval) { this.name = name; this.approval = approval; }
        @Override public String name() { return name; }
        @Override public String description() { return "fake"; }
        @Override public boolean requiresApproval() { return approval; }
        @Override public ToolResult execute(MapLike arguments, ToolContext context) { return ToolResult.ok(java.util.Map.of(), ""); }
    }

    private ToolRegistry registryWith(Tool... tools) {
        ToolRegistry reg = mock(ToolRegistry.class);
        for (Tool t : tools) {
            when(reg.get(t.name())).thenReturn(Optional.of(t));
        }
        return reg;
    }

    @Test
    void rejectsPlanWithUnknownTool() {
        ToolRegistry reg = registryWith(new FakeTool("query_incidents", false));
        AgentPolicy policy = new AgentPolicy(reg);
        policy.setMaxSteps(8);

        AgentPlan plan = AgentPlan.builder()
                .goal("测试")
                .steps(List.of(AgentStep.builder().id("s1").tool("unknown_tool").build()))
                .build();
        assertThrows(AgentException.class, () -> policy.validatePlan(plan));
    }

    @Test
    void overridesApprovalFlag() {
        ToolRegistry reg = registryWith(new FakeTool("create_task", true));
        AgentPolicy policy = new AgentPolicy(reg);
        policy.setMaxSteps(8);

        AgentStep step = AgentStep.builder()
                .id("s1").tool("create_task")
                .requiresApproval(false)  // 模型想绕过审批
                .build();
        AgentPlan plan = AgentPlan.builder().goal("x").steps(List.of(step)).build();

        policy.validatePlan(plan);
        // 服务端覆盖:必须为 true
        assertEquals(Boolean.TRUE, step.getRequiresApproval());
    }

    @Test
    void rejectsPlanOverMaxSteps() {
        ToolRegistry reg = registryWith(new FakeTool("query_incidents", false));
        AgentPolicy policy = new AgentPolicy(reg);
        policy.setMaxSteps(2);

        AgentPlan plan = AgentPlan.builder()
                .goal("x")
                .steps(List.of(
                        AgentStep.builder().id("s1").tool("query_incidents").build(),
                        AgentStep.builder().id("s2").tool("query_incidents").build(),
                        AgentStep.builder().id("s3").tool("query_incidents").build()
                ))
                .build();
        assertThrows(AgentException.class, () -> policy.validatePlan(plan));
    }

    @Test
    void rejectsDuplicateStepId() {
        ToolRegistry reg = registryWith(new FakeTool("query_incidents", false));
        AgentPolicy policy = new AgentPolicy(reg);
        policy.setMaxSteps(8);

        AgentPlan plan = AgentPlan.builder()
                .goal("x")
                .steps(List.of(
                        AgentStep.builder().id("s1").tool("query_incidents").build(),
                        AgentStep.builder().id("s1").tool("query_incidents").build()
                ))
                .build();
        assertThrows(AgentException.class, () -> policy.validatePlan(plan));
    }

    @Test
    void rejectsOversizedArgs() {
        ToolRegistry reg = registryWith(new FakeTool("query_incidents", false));
        AgentPolicy policy = new AgentPolicy(reg);
        policy.setMaxSteps(8);

        String huge = "x".repeat(10000);
        AgentPlan plan = AgentPlan.builder()
                .goal("x")
                .steps(List.of(AgentStep.builder()
                        .id("s1").tool("query_incidents")
                        .arguments(java.util.Map.of("query", huge))
                        .build()))
                .build();
        assertThrows(AgentException.class, () -> policy.validatePlan(plan));
    }
}
