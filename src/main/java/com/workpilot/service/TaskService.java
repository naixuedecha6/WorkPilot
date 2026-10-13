package com.workpilot.service;

import com.workpilot.dto.TaskDtos;
import com.workpilot.entity.Task;
import com.workpilot.entity.TaskPriority;
import com.workpilot.entity.TaskStatus;
import com.workpilot.exception.AgentException;
import com.workpilot.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public List<Task> list(Long userId) {
        return taskRepository.findByCreatorIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public Task create(Long userId, TaskDtos.CreateRequest req) {
        Task t = Task.builder()
                .creatorId(userId)
                .title(req.title())
                .description(req.description())
                .priority(req.priority() == null ? TaskPriority.MEDIUM : req.priority())
                .status(TaskStatus.OPEN)
                .dueAt(req.dueAt())
                .createdAt(Instant.now())
                .build();
        return taskRepository.save(t);
    }

    @Transactional
    public Task updateStatus(Long userId, Long id, TaskStatus newStatus) {
        Task t = mustOwn(userId, id);
        t.setStatus(newStatus);
        return taskRepository.save(t);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Task t = mustOwn(userId, id);
        taskRepository.delete(t);
    }

    private Task mustOwn(Long userId, Long id) {
        Task t = taskRepository.findById(id)
                .orElseThrow(() -> new AgentException("TASK_NOT_FOUND", "待办不存在: " + id));
        if (!t.getCreatorId().equals(userId)) {
            throw new AgentException("FORBIDDEN", "无权操作该待办");
        }
        return t;
    }
}
