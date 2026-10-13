package com.workpilot.repository;

import com.workpilot.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByCreatorIdOrderByCreatedAtDesc(Long creatorId);
    Optional<Task> findByCreatorIdAndIdempotencyKey(Long creatorId, String key);
}
