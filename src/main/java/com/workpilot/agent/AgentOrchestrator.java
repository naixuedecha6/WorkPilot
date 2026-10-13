package com.workpilot.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpilot.dto.*;
import com.workpilot.entity.*;
import com.workpilot.exception.AgentException;
import com.workpilot.repository.AgentRunRepository;
import com.workpilot.repository.ApprovalRepository;
import com.workpilot.repository.ToolExecutionRepository;
import com.workpilot.service.ApprovalService;
import com.workpilot.service.UserContextService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Agent 编排器(对应文档 §7.3 AgentOrchestrator):
 * 串联规划、执行、暂停、恢复与结束;所有状态迁移由本类控制。
 *
 * <p>状态机(对应文档 §7.2):</p>
 * <pre>
 * CREATED → PLANNING → RUNNING ─→ COMPLETED
 *                        │   ─→ FAILED
 *                        │   ─→ WAITING_APPROVAL ─→ RUNNING(恢复) / REJECTED
 * </pre>
 *
 * <p>运行轨迹通过 {@link ToolExecutionRepository#findByRunIdOrderByCreatedAtAsc} 查询,
 * 步骤状态在数据库里逐条可查,便于演示和调试。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AgentOrchestrator {

    private final AgentRunRepository runRepository;
    private final ToolExecutionRepository executionRepository;
    private final ApprovalRepository approvalRepository;
    private final AgentPlanner planner;
    private final AgentPolicy policy;
    private final AgentExecutor executor;
    private final ApprovalService approvalService;
    private final UserContextService userContextService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 从头启动一个运行 */
    @Transactional
    public AgentRun start(String userInput, Long userId, Long conversationId) {
        AgentRun run = AgentRun.builder()
                .userId(userId)
                .conversationId(conversationId)
                .status(AgentStatus.CREATED)
                .input(userInput)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        run = runRepository.save(run);

        try {
            run.setStatus(AgentStatus.PLANNING);
            run.setStartedAt(Instant.now());
            run.setUpdatedAt(Instant.now());
            runRepository.save(run);

            AgentPlan plan = planner.createPlan(userInput);
            policy.validatePlan(plan);
            run.setPlan(toJson(plan));
            run.setStatus(AgentStatus.RUNNING);
            run.setUpdatedAt(Instant.now());
            runRepository.save(run);

            return runLoop(run, plan, 0, new ArrayList<>());
        } catch (AgentException e) {
            return failRun(run, e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error in run {}", run.getId(), e);
            return failRun(run, "UNEXPECTED", e.getMessage());
        }
    }

    /**
     * 审批通过后,从被暂停的步骤继续执行。
     * 不重新生成计划,不重做已完成步骤。
     */
    @Transactional
    public AgentRun resumeFromApproval(Long runId, Long reviewerId) {
        AgentRun run = mustGetRun(runId);
        if (run.getStatus() != AgentStatus.WAITING_APPROVAL) {
            throw new AgentException("RUN_NOT_PAUSABLE", "运行不在等待审批状态: " + run.getStatus());
        }

        AgentPlan plan = fromJson(run.getPlan(), AgentPlan.class);
        // 找到该 run 下的 PENDING 审批,把它改成 APPROVED
        Approval approval = approvalRepository
                .findByStatusOrderByCreatedAtAsc(ApprovalStatus.PENDING).stream()
                .filter(a -> a.getRunId().equals(runId))
                .findFirst()
                .orElseThrow(() -> new AgentException("APPROVAL_NOT_FOUND", "找不到 PENDING 审批"));
        approval.setStatus(ApprovalStatus.APPROVED);
        approval.setReviewerId(reviewerId);
        approval.setReviewedAt(Instant.now());
        approvalRepository.save(approval);

        // 把对应 ToolExecution 标记为 PENDING(等下被真正执行)
        ToolExecution te = executionRepository.findById(approval.getToolExecutionId())
                .orElseThrow(() -> new AgentException("EXECUTION_NOT_FOUND", "执行记录不存在"));
        te.setStatus(ToolExecutionStatus.PENDING);
        executionRepository.save(te);

        // 恢复运行
        run.setStatus(AgentStatus.RUNNING);
        run.setUpdatedAt(Instant.now());
        runRepository.save(run);

        // 推断当前 step index(已 SUCCESS/FAILED 数量)
        int currentIdx = (int) executionRepository.findByRunIdOrderByCreatedAtAsc(runId).stream()
                .filter(t -> t.getStatus() == ToolExecutionStatus.SUCCESS
                          || t.getStatus() == ToolExecutionStatus.FAILED)
                .count();
        // 关键:把当前 step 的审批标记清掉,runLoop 才会真正执行它
        if (currentIdx < plan.getSteps().size()) {
            plan.getSteps().get(currentIdx).setRequiresApproval(false);
        }
        List<StepResult> previous = loadPreviousResults(run, currentIdx);
        return runLoop(run, plan, currentIdx, previous);
    }

    @Transactional
    public AgentRun rejectApproval(Long runId, Long reviewerId, String comment) {
        AgentRun run = mustGetRun(runId);
        List<Approval> pendings = approvalRepository.findByStatusOrderByCreatedAtAsc(ApprovalStatus.PENDING)
                .stream().filter(a -> a.getRunId().equals(runId)).toList();
        if (pendings.isEmpty()) {
            throw new AgentException("APPROVAL_NOT_PENDING", "没有待审批的请求");
        }
        for (Approval a : pendings) {
            a.setStatus(ApprovalStatus.REJECTED);
            a.setReviewerId(reviewerId);
            a.setReviewedAt(Instant.now());
            a.setComment(comment);
            approvalRepository.save(a);
        }
        run.setStatus(AgentStatus.REJECTED);
        run.setFinishedAt(Instant.now());
        run.setUpdatedAt(Instant.now());
        return runRepository.save(run);
    }

    // ---------------- 内部循环 ----------------

    private AgentRun runLoop(AgentRun run, AgentPlan plan, int fromIndex, List<StepResult> previous) {
        ToolContext ctx = ToolContext.builder()
                .userId(run.getUserId())
                .username(userContextService.getUsername(run.getUserId()))
                .runId(run.getId())
                .conversationId(run.getConversationId())
                .previousResults(previous)
                .build();

        int executed = 0;
        for (int i = fromIndex; i < plan.getSteps().size(); i++) {
            if (executed >= policy.maxSteps()) {
                return failRun(run, "STEP_LIMIT", "超过最大执行步数 " + policy.maxSteps());
            }
            AgentStep step = plan.getSteps().get(i);

            if (Boolean.TRUE.equals(step.getRequiresApproval())) {
                // 先创建一条 PENDING 的 ToolExecution(代表"准备执行"),再创建审批
                ToolExecution pending = ToolExecution.builder()
                        .runId(run.getId())
                        .stepId(step.getId())
                        .toolName(step.getTool())
                        .status(ToolExecutionStatus.PENDING)
                        .arguments(toJson(step.getArguments() == null ? Map.of() : step.getArguments()))
                        .createdAt(Instant.now())
                        .build();
                pending = executionRepository.save(pending);
                approvalService.createForStep(run, step, pending);

                run.setStatus(AgentStatus.WAITING_APPROVAL);
                run.setUpdatedAt(Instant.now());
                return runRepository.save(run);
            }

            StepResult result = executor.execute(run.getId(), step, ctx);
            previous.add(result);
            if (!result.isSuccess()) {
                return failRun(run,
                        result.getErrorCode() == null ? "TOOL_FAILED" : result.getErrorCode(),
                        "步骤 " + step.getId() + " 失败: " + result.getErrorMessage());
            }
            executed++;
        }

        // 全部完成,生成汇总
        String summary = summarize(plan, previous);
        run.setStatus(AgentStatus.COMPLETED);
        run.setOutput(summary);
        run.setFinishedAt(Instant.now());
        run.setUpdatedAt(Instant.now());
        return runRepository.save(run);
    }

    private AgentRun failRun(AgentRun run, String code, String message) {
        run.setStatus(AgentStatus.FAILED);
        run.setErrorMessage("[" + code + "] " + message);
        run.setFinishedAt(Instant.now());
        run.setUpdatedAt(Instant.now());
        return runRepository.save(run);
    }

    private AgentRun mustGetRun(Long runId) {
        return runRepository.findById(runId)
                .orElseThrow(() -> new AgentException("RUN_NOT_FOUND", "运行不存在: " + runId));
    }

    private List<StepResult> loadPreviousResults(AgentRun run, int upToIndex) {
        List<ToolExecution> execs = executionRepository.findByRunIdOrderByCreatedAtAsc(run.getId());
        List<StepResult> out = new ArrayList<>();
        for (int i = 0; i < upToIndex && i < execs.size(); i++) {
            ToolExecution e = execs.get(i);
            Map<String, Object> data = new HashMap<>();
            String summary = null;
            if (e.getResult() != null) {
                try {
                    Map<?, ?> m = objectMapper.readValue(e.getResult(), Map.class);
                    Object s = m.get("summary");
                    Object d = m.get("data");
                    summary = s == null ? null : s.toString();
                    if (d instanceof Map<?, ?> dm) {
                        dm.forEach((k, v) -> data.put(String.valueOf(k), v));
                    }
                } catch (Exception ignored) { }
            }
            out.add(StepResult.builder()
                    .stepId(e.getStepId())
                    .tool(e.getToolName())
                    .success(e.getStatus() == ToolExecutionStatus.SUCCESS)
                    .data(data)
                    .summary(summary)
                    .errorCode(e.getErrorCode())
                    .build());
        }
        return out;
    }

    private String summarize(AgentPlan plan, List<StepResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("任务完成。共执行 ").append(results.size()).append(" 个步骤:\n\n");
        for (StepResult r : results) {
            sb.append("- [").append(r.getStepId()).append("] ").append(r.getTool());
            if (r.getSummary() != null) sb.append(": ").append(r.getSummary());
            sb.append("\n");
        }
        return sb.toString();
    }

    private String toJson(Object o) {
        try { return objectMapper.writeValueAsString(o); }
        catch (JsonProcessingException e) { throw new AgentException("JSON_ERROR", e.getMessage()); }
    }

    private <T> T fromJson(String s, Class<T> type) {
        try { return objectMapper.readValue(s, type); }
        catch (Exception e) { throw new AgentException("JSON_ERROR", e.getMessage()); }
    }
}
