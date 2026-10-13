package com.workpilot.controller;

import com.workpilot.agent.AgentOrchestrator;
import com.workpilot.entity.Approval;
import com.workpilot.entity.ApprovalStatus;
import com.workpilot.repository.ApprovalRepository;
import com.workpilot.service.UserContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/approvals")
@RequiredArgsConstructor
public class ApprovalController {

    private final ApprovalRepository approvalRepository;
    private final AgentOrchestrator orchestrator;
    private final UserContextService userContextService;

    /** 当前所有 PENDING 审批 */
    @GetMapping
    public List<Approval> listPending() {
        return approvalRepository.findByStatusOrderByCreatedAtAsc(ApprovalStatus.PENDING);
    }

    /** 批准并恢复运行 */
    @PostMapping("/{id}/approve")
    @Transactional
    public Map<String, Object> approve(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        Approval a = approvalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审批不存在"));
        if (a.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("审批已处理,当前状态: " + a.getStatus());
        }
        var run = orchestrator.resumeFromApproval(a.getRunId(), userContextService.currentUserId());
        return Map.of(
                "approvalId", id,
                "runId", run.getId(),
                "status", run.getStatus().name(),
                "output", run.getOutput() == null ? "" : run.getOutput()
        );
    }

    @PostMapping("/{id}/reject")
    @Transactional
    public Map<String, Object> reject(@PathVariable Long id, @RequestBody(required = false) Map<String, String> body) {
        Approval a = approvalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("审批不存在"));
        if (a.getStatus() != ApprovalStatus.PENDING) {
            throw new IllegalStateException("审批已处理,当前状态: " + a.getStatus());
        }
        String comment = body == null ? null : body.get("comment");
        var run = orchestrator.rejectApproval(a.getRunId(), userContextService.currentUserId(), comment);
        return Map.of("approvalId", id, "runId", run.getId(), "status", run.getStatus().name());
    }
}
