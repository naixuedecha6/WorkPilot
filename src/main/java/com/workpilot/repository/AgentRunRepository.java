package com.workpilot.repository;

import com.workpilot.entity.AgentRun;
import com.workpilot.entity.AgentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AgentRunRepository extends JpaRepository<AgentRun, Long> {
    List<AgentRun> findByUserIdOrderByCreatedAtDesc(Long userId);
    List<AgentRun> findByStatusOrderByCreatedAtAsc(AgentStatus status);
}
