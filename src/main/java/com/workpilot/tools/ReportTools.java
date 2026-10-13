package com.workpilot.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpilot.dto.StepResult;
import com.workpilot.dto.ToolContext;
import com.workpilot.dto.ToolResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 报告生成:把前面步骤累积的数据(故障/知识/统计)整合成结构化报告。
 */
@Component
public class ReportTools implements Tool {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ReportTools(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override public String name() { return "generate_report"; }

    @Override
    public String description() {
        return "基于前面步骤的检索/统计结果生成结构化报告。" +
                "参数:title(报告标题,可选)。无需审批。";
    }

    @Override
    public ToolResult execute(MapLike arguments, ToolContext context) {
        String title = arguments.getString("title");
        if (title == null || title.isBlank()) title = "故障分析报告";

        String evidence = buildEvidenceText(context);
        String systemPrompt = """
                你是 WorkPilot 的报告生成助手。基于提供的"证据"内容,生成结构化分析报告。
                要求:
                1. 区分"有证据支持的事实"和"模型归纳的建议",不要把推测表述为已确认事实
                2. 涉及知识库内容时,标明来源(用 [来源 N] 引用编号)
                3. 末尾给出"尚未确认的假设"与"数据局限"
                4. 使用 Markdown,中文输出
                """;
        String userMsg = "报告标题: " + title + "\n\n证据:\n" + evidence;

        String body = chatClient.prompt()
                .system(systemPrompt)
                .user(userMsg)
                .call()
                .content();

        return ToolResult.ok(Map.of(
                "title", title,
                "content", body == null ? "" : body
        ), "已生成报告: " + title);
    }

    private String buildEvidenceText(ToolContext context) {
        if (context.getPreviousResults() == null || context.getPreviousResults().isEmpty()) {
            return "(无前置数据)";
        }
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        for (StepResult r : context.getPreviousResults()) {
            sb.append("--- [来源 ").append(idx++).append("] 工具: ")
              .append(r.getTool()).append(" 步骤: ").append(r.getStepId()).append(" ---\n");
            if (r.getData() != null) {
                try { sb.append(objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(r.getData())); }
                catch (JsonProcessingException e) { sb.append(r.getData()); }
            } else if (r.getSummary() != null) {
                sb.append(r.getSummary());
            }
            sb.append("\n\n");
        }
        return sb.toString();
    }
}
