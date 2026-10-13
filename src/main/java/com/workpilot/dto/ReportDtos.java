package com.workpilot.dto;

import com.workpilot.entity.AgentStatus;

import java.time.Instant;
import java.util.List;

public final class ReportDtos {

    private ReportDtos() {}

    /** 报告列表项 */
    public record ReportSummary(
            Long runId,
            Long toolExecutionId,
            String title,
            AgentStatus runStatus,
            String input,
            String createdAt,
            String sourceQuery) {}

    public record ReportListResponse(
            int total,
            List<ReportSummary> rows) {}

    /** 报告详情 */
    public record ReportDetail(
            Long runId,
            String title,
            String content,
            AgentStatus runStatus,
            String runInput,
            String runOutput,
            String errorMessage,
            String createdAt,
            List<Evidence> evidence) {}

    /** 报告里的证据(其他工具的输出) */
    public record Evidence(
            String stepId,
            String tool,
            String summary,
            String data) {}
}
