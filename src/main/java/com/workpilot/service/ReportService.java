package com.workpilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpilot.dto.ReportDtos;
import com.workpilot.entity.AgentRun;
import com.workpilot.entity.AgentStatus;
import com.workpilot.entity.ToolExecution;
import com.workpilot.entity.ToolExecutionStatus;
import com.workpilot.exception.AgentException;
import com.workpilot.repository.AgentRunRepository;
import com.workpilot.repository.ToolExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 报告中心:报告是 Agent 调用 {@code generate_report} 工具的产物,
 * 不单独建表,而是从 tool_executions + agent_runs 拼装。
 *
 * <p>好处:不破坏现有数据;写操作只有一处。
 * 坏处:不能独立编辑/删除报告 — 也不应该有这需求。</p>
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    private static final String REPORT_TOOL = "generate_report";

    private final ToolExecutionRepository executionRepository;
    private final AgentRunRepository runRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional(readOnly = true)
    public ReportDtos.ReportListResponse list(Long userId) {
        List<ToolExecution> reportExecs = executionRepository
                .findByToolNameAndStatusOrderByCreatedAtDesc(REPORT_TOOL, ToolExecutionStatus.SUCCESS);

        List<ReportDtos.ReportSummary> rows = new ArrayList<>();
        for (ToolExecution te : reportExecs) {
            AgentRun run = runRepository.findById(te.getRunId()).orElse(null);
            if (run == null || !run.getUserId().equals(userId)) continue;

            Map<String, Object> args = parseJsonMap(te.getArguments());
            Map<String, Object> result = parseJsonMap(te.getResult());
            Map<String, Object> data = mapOrEmpty(result.get("data"));
            String title = stringOrDefault(args.get("title"),
                              stringOrDefault(data.get("title"), "故障分析报告"));
            String content = stringOrDefault(data.get("title"), title);

            rows.add(new ReportDtos.ReportSummary(
                    run.getId(),
                    te.getId(),
                    title,
                    run.getStatus(),
                    run.getInput(),
                    te.getCreatedAt() == null ? null : te.getCreatedAt().toString(),
                    content
            ));
        }
        return new ReportDtos.ReportListResponse(rows.size(), rows);
    }

    @Transactional(readOnly = true)
    public ReportDtos.ReportDetail get(Long userId, Long runId) {
        AgentRun run = runRepository.findById(runId)
                .orElseThrow(() -> new AgentException("RUN_NOT_FOUND", "运行不存在: " + runId));
        if (!run.getUserId().equals(userId)) {
            throw new AgentException("FORBIDDEN", "无权访问该报告");
        }

        // 找 generate_report 这一步
        List<ToolExecution> execs = executionRepository.findByRunIdOrderByCreatedAtAsc(runId);
        ToolExecution reportExec = execs.stream()
                .filter(e -> REPORT_TOOL.equals(e.getToolName()))
                .filter(e -> e.getStatus() == ToolExecutionStatus.SUCCESS)
                .reduce((first, second) -> second)   // 多个取最后一个
                .orElseThrow(() -> new AgentException("REPORT_NOT_FOUND", "本次运行未生成报告"));

        Map<String, Object> args = parseJsonMap(reportExec.getArguments());
        Map<String, Object> result = parseJsonMap(reportExec.getResult());
        // result 结构: { "success": true, "summary": "...", "data": { "title": "...", "content": "..." } }
        Map<String, Object> data = mapOrEmpty(result.get("data"));
        String title = stringOrDefault(args.get("title"),
                          stringOrDefault(data.get("title"), "故障分析报告"));
        String content = stringOrDefault(data.get("content"), "(报告内容为空)");

        // 收集证据:其它步骤的 summary + data 摘要
        List<ReportDtos.Evidence> evidence = new ArrayList<>();
        for (ToolExecution e : execs) {
            if (REPORT_TOOL.equals(e.getToolName())) continue;
            if (e.getStatus() != ToolExecutionStatus.SUCCESS) continue;
            Map<String, Object> r = parseJsonMap(e.getResult());
            evidence.add(new ReportDtos.Evidence(
                    e.getStepId(),
                    e.getToolName(),
                    stringOrDefault(r.get("summary"), null),
                    renderData(r.get("data"))
            ));
        }

        return new ReportDtos.ReportDetail(
                run.getId(),
                title,
                content,
                run.getStatus(),
                run.getInput(),
                run.getOutput(),
                run.getErrorMessage(),
                run.getCreatedAt() == null ? null : run.getCreatedAt().toString(),
                evidence);
    }

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return objectMapper.readValue(json, Map.class); }
        catch (Exception e) { return Map.of(); }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOrEmpty(Object o) {
        return (o instanceof Map<?, ?> m) ? (Map<String, Object>) m : Map.of();
    }

    private String stringOrDefault(Object o, String def) {
        if (o == null) return def;
        String s = o.toString();
        return s.isBlank() ? def : s;
    }

    /** 把 data 字段渲染成简短 JSON 字符串,避免过大 */
    private String renderData(Object data) {
        if (data == null) return null;
        try {
            String s = objectMapper.writeValueAsString(data);
            return s.length() > 500 ? s.substring(0, 500) + "..." : s;
        } catch (Exception e) {
            return data.toString();
        }
    }
}
