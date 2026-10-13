package com.workpilot.agent;

import com.workpilot.dto.AgentPlan;
import com.workpilot.dto.AgentStep;
import com.workpilot.exception.AgentException;
import com.workpilot.tools.Tool;
import com.workpilot.tools.ToolRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Agent 策略(对应文档 §7.3 AgentPolicy):负责:
 * <ul>
 *   <li>校验计划是否合法(工具是否在白名单、字段是否齐备)</li>
 *   <li>限制单次运行的最大步骤数</li>
 *   <li>校验步骤参数范围、长度</li>
 *   <li>补充 requiresApproval 标记,以 Tool 实现声明为准(模型不能决定是否需要审批)</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AgentPolicy {

    private final ToolRegistry toolRegistry;

    @Value("${workpilot.agent.max-steps:8}")
    private int maxSteps;

    public int maxSteps() {
        return maxSteps;
    }

    /** 测试用 setter,反射不到 @Value 时手动设 */
    public void setMaxSteps(int maxSteps) {
        this.maxSteps = maxSteps;
    }

    public void validatePlan(AgentPlan plan) {
        if (plan == null || plan.getSteps() == null || plan.getSteps().isEmpty()) {
            throw new AgentException("INVALID_PLAN", "计划为空");
        }
        if (plan.getSteps().size() > maxSteps) {
            throw new AgentException("PLAN_TOO_LONG",
                    "计划步骤数 " + plan.getSteps().size() + " 超过最大允许 " + maxSteps);
        }
        Set<String> seen = new HashSet<>();
        for (AgentStep step : plan.getSteps()) {
            if (step.getId() == null || step.getId().isBlank()) {
                throw new AgentException("INVALID_PLAN", "步骤缺少 id");
            }
            if (!seen.add(step.getId())) {
                throw new AgentException("INVALID_PLAN", "步骤 id 重复: " + step.getId());
            }
            Tool tool = toolRegistry.get(step.getTool())
                    .orElseThrow(() -> new AgentException("TOOL_NOT_ALLOWED",
                            "工具不在白名单中: " + step.getTool()));
            // 审批标记以服务端 Tool 声明为准,防止模型绕过
            step.setRequiresApproval(tool.requiresApproval());
            validateStepArgs(step);
        }
    }

    private void validateStepArgs(AgentStep step) {
        Map<String, Object> args = step.getArguments() == null ? Map.of() : step.getArguments();
        // 通用参数上限:任何 key 长度、数组大小,防止模型塞巨型 payload
        for (Map.Entry<String, Object> e : args.entrySet()) {
            if (e.getKey().length() > 64) {
                throw new AgentException("INVALID_ARGS", "参数 key 过长");
            }
            if (e.getValue() instanceof String s && s.length() > 8000) {
                throw new AgentException("INVALID_ARGS", "字符串参数过长: " + e.getKey());
            }
            if (e.getValue() instanceof List<?> list && list.size() > 200) {
                throw new AgentException("INVALID_ARGS", "数组参数过大: " + e.getKey());
            }
        }
    }
}
