package com.workpilot.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpilot.dto.AgentStep;
import com.workpilot.dto.StepResult;
import com.workpilot.dto.ToolContext;
import com.workpilot.dto.ToolResult;
import com.workpilot.entity.ToolExecution;
import com.workpilot.entity.ToolExecutionStatus;
import com.workpilot.exception.AgentException;
import com.workpilot.repository.ToolExecutionRepository;
import com.workpilot.tools.Args;
import com.workpilot.tools.Tool;
import com.workpilot.tools.ToolRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 单步执行器:负责执行一个步骤,记录 ToolExecution,处理超时与失败。
 * 不直接驱动状态机,只关注"执行一步"这一件事。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentExecutor {

    private final ToolRegistry toolRegistry;
    private final ToolExecutionRepository executionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public StepResult execute(Long runId, AgentStep step, ToolContext context) {
        Tool tool = toolRegistry.get(step.getTool())
                .orElseThrow(() -> new AgentException("TOOL_NOT_ALLOWED",
                        "工具不在白名单中: " + step.getTool()));

        Instant start = Instant.now();
        ToolExecution exec = ToolExecution.builder()
                .runId(runId)
                .stepId(step.getId())
                .toolName(tool.name())
                .status(ToolExecutionStatus.RUNNING)
                .arguments(toJson(step.getArguments()))
                .createdAt(Instant.now())
                .build();
        exec = executionRepository.save(exec);

        try {
            Tool.MapLike args = Args.of(step.getArguments()).asMapLike();
            ToolResult result = tool.execute(args, context);
            long ms = Duration.between(start, Instant.now()).toMillis();
            exec.setDurationMs(ms);
            exec.setArguments(toJson(step.getArguments()));
            exec.setResult(toJson(Map.of(
                    "success", result.isSuccess(),
                    "summary", result.getSummary() == null ? "" : result.getSummary(),
                    "data", result.getData() == null ? Map.of() : result.getData()
            )));
            if (result.isSuccess()) {
                exec.setStatus(ToolExecutionStatus.SUCCESS);
                executionRepository.save(exec);
                return StepResult.builder()
                        .stepId(step.getId())
                        .tool(tool.name())
                        .success(true)
                        .data(result.getData())
                        .summary(result.getSummary())
                        .build();
            } else {
                exec.setStatus(ToolExecutionStatus.FAILED);
                exec.setErrorCode(result.getErrorCode());
                executionRepository.save(exec);
                return StepResult.builder()
                        .stepId(step.getId())
                        .tool(tool.name())
                        .success(false)
                        .errorCode(result.getErrorCode())
                        .errorMessage(result.getErrorMessage())
                        .summary("工具执行失败: " + result.getErrorMessage())
                        .build();
            }
        } catch (Exception e) {
            long ms = Duration.between(start, Instant.now()).toMillis();
            exec.setStatus(ToolExecutionStatus.FAILED);
            exec.setErrorCode("EXCEPTION");
            exec.setDurationMs(ms);
            exec.setResult(toJson(Map.of("error", e.getMessage() == null ? "" : e.getMessage())));
            executionRepository.save(exec);
            log.error("Tool execution exception: {} {}", tool.name(), e.getMessage(), e);
            return StepResult.builder()
                    .stepId(step.getId())
                    .tool(tool.name())
                    .success(false)
                    .errorCode("EXCEPTION")
                    .errorMessage(e.getMessage())
                    .summary("工具异常: " + e.getMessage())
                    .build();
        }
    }

    private String toJson(Object o) {
        if (o == null) return null;
        try { return objectMapper.writeValueAsString(o); }
        catch (JsonProcessingException e) { return String.valueOf(o); }
    }
}
