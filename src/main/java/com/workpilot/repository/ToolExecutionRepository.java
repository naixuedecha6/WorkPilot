package com.workpilot.repository;

import com.workpilot.entity.ToolExecution;
import com.workpilot.entity.ToolExecutionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ToolExecutionRepository extends JpaRepository<ToolExecution, Long> {
    List<ToolExecution> findByRunIdOrderByCreatedAtAsc(Long runId);
    List<ToolExecution> findByToolNameAndStatusOrderByCreatedAtDesc(String toolName, ToolExecutionStatus status);
}
