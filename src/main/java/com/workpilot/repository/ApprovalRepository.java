package com.workpilot.repository;

import com.workpilot.entity.Approval;
import com.workpilot.entity.ApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApprovalRepository extends JpaRepository<Approval, Long> {
    List<Approval> findByStatusOrderByCreatedAtAsc(ApprovalStatus status);
    Optional<Approval> findByRunIdAndToolExecutionIdAndStatus(Long runId, Long toolExecutionId, ApprovalStatus status);
    List<Approval> findByReviewerIdOrderByCreatedAtDesc(Long reviewerId);
}
