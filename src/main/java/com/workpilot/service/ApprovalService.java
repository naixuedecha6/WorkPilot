package com.workpilot.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpilot.dto.AgentStep;
import com.workpilot.dto.ToolContext;
import com.workpilot.entity.AgentRun;
import com.workpilot.entity.Approval;
import com.workpilot.entity.ApprovalStatus;
import com.workpilot.repository.ApprovalRepository;
import com.workpilot.entity.ToolExecution;
import com.workpilot.repository.ToolExecutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * 审批服务:负责创建、查询、批准、拒绝审批。
 * 设计要点(对应文档 §6.5、§9.3):
 * <ul>
 *   <li>同一 run + tool_execution 不能重复创建 PENDING 审批(由 DB 唯一索引兜底)</li>
 *   <li>批准时必须重新校验权限(已在控制器层做一次,这里在事务内再次确认)</li>
 *   <li>幂等:已处理的审批不能再被处理</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ApprovalService {

    private final ApprovalRepository approvalRepository;
    private final ToolExecutionRepository executionRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public Approval createForStep(AgentRun run, AgentStep step, ToolExecution pending) {
        Map<String, Object> payload = Map.of(
                "tool", step.getTool(),
                "purpose", step.getPurpose() == null ? "" : step.getPurpose(),
                "arguments", step.getArguments() == null ? Map.of() : step.getArguments()
        );
        Approval approval = Approval.builder()
                .runId(run.getId())
                .toolExecutionId(pending.getId())
                .toolName(step.getTool())
                .status(ApprovalStatus.PENDING)
                .payload(toJson(payload))
                .createdAt(Instant.now())
                .build();
        return approvalRepository.save(approval);
    }
    
    private String toJson(Object o) {
        try { return objectMapper.writeValueAsString(o); }
        catch (JsonProcessingException e) { return "{}"; }
    }
}
