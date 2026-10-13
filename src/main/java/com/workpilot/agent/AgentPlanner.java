package com.workpilot.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpilot.dto.AgentPlan;
import com.workpilot.exception.AgentException;
import com.workpilot.tools.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Agent 计划器:把用户请求转成结构化、有限的任务计划(对应文档 §5.2)。
 *
 * <p>设计要点:</p>
 * <ul>
 *   <li>Plan-and-Execute 而不是纯 ReAct:先生成完整计划,经策略校验,再逐步执行</li>
 *   <li>工具名称必须从 ToolRegistry 白名单中选取</li>
 *   <li>写操作步骤的 requiresApproval 由服务端 Tool 声明强制覆盖</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentPlanner {

    private final ChatClient chatClient;
    private final ToolRegistry toolRegistry;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentPlan createPlan(String userInput) {
        String toolDesc = formatToolList();
        String system = """
                你是 WorkPilot 的任务规划器。根据用户的请求,把它拆成一系列结构化步骤。
                只允许使用下面列表里的工具,不能使用任何其他工具。
                每个步骤必须有 id、tool、purpose;如果是写操作,标注 requiresApproval=true。
                严格输出 JSON,不要 Markdown 包装,不要任何解释文字。

                JSON 结构:
                {
                  "goal": "用户目标的一句话描述",
                  "steps": [
                    { "id": "step-1", "tool": "工具名", "purpose": "这一步要做什么", "arguments": { ... }, "requiresApproval": false }
                  ]
                }

                可用工具:
                %s
                """.formatted(toolDesc);

        String raw = chatClient.prompt()
                .system(system)
                .user(userInput)
                .call()
                .content();
        log.debug("Planner raw output: {}", raw);

        AgentPlan plan = parsePlan(raw);
        if (plan.getSteps() == null || plan.getSteps().isEmpty()) {
            throw new AgentException("PLANNER_EMPTY", "模型未返回有效步骤,原话: " + truncate(raw, 200));
        }
        return plan;
    }

    private String formatToolList() {
        StringBuilder sb = new StringBuilder();
        for (Map<String, String> t : toolRegistry.describeAll()) {
            sb.append("- ").append(t.get("name"))
              .append(t.get("requiresApproval").equals("true") ? " [需要审批]" : "")
              .append(": ").append(t.get("description"))
              .append("\n");
        }
        return sb.toString();
    }

    private AgentPlan parsePlan(String raw) {
        String json = extractJson(raw);
        try {
            return objectMapper.readValue(json, AgentPlan.class);
        } catch (Exception e) {
            throw new AgentException("PLANNER_PARSE_ERROR", "无法解析计划 JSON: " + e.getMessage());
        }
    }

    /** 从模型输出里抠出 JSON 块(可能被 ```json ... ``` 包裹) */
    private String extractJson(String raw) {
        if (raw == null) return "{}";
        String trimmed = raw.trim();
        if (trimmed.startsWith("{")) return trimmed;
        int start = trimmed.indexOf("```");
        if (start >= 0) {
            int codeStart = trimmed.indexOf('\n', start);
            int codeEnd = trimmed.indexOf("```", codeStart + 1);
            if (codeStart >= 0 && codeEnd > codeStart) {
                return trimmed.substring(codeStart + 1, codeEnd).trim();
            }
        }
        int braceStart = trimmed.indexOf('{');
        int braceEnd = trimmed.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }
        return "{}";
    }

    private String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
