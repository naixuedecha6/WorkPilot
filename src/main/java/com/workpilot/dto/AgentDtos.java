package com.workpilot.dto;

import com.workpilot.entity.ToolExecution;

import java.util.List;
import java.util.Map;

public final class AgentDtos {

    private AgentDtos() {}

    /** 运行列表 / 详情共用 */
    public record RunSummary(
            Long id,
            String status,
            String input,
            String output,
            String errorMessage,
            String startedAt,
            String finishedAt,
            String createdAt) {}

    /** 单条工具执行 */
    public record ExecutionDto(
            Long id,
            String stepId,
            String toolName,
            String status,
            String arguments,
            Long durationMs,
            String errorCode,
            String summary,
            Map<String, Object> data) {

        public static ExecutionDto from(ToolExecution e) {
            return new ExecutionDto(
                    e.getId(), e.getStepId(), e.getToolName(),
                    e.getStatus().name(), e.getArguments(), e.getDurationMs(),
                    e.getErrorCode(), null, null);
        }
    }

    /** 知识库命中片段,用于 RAG 引用展示 */
    public record Citation(
            String documentName,
            Integer chunkIndex,
            Double score,
            String content) {}

    /** trace 接口的完整响应 */
    public record TraceResponse(
            RunSummary run,
            String planJson,
            Object plan,                     // 解析后的对象,失败时为 null
            String output,
            String errorMessage,
            List<ExecutionDto> executions,
            List<Citation> citations) {}      // 所有 search_knowledge 步骤的命中
}
