package com.workpilot.dto;

import com.workpilot.entity.Task;
import com.workpilot.entity.TaskPriority;
import com.workpilot.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class TaskDtos {

    private TaskDtos() {}

    public record TaskResponse(
            Long id,
            String title,
            String description,
            TaskPriority priority,
            TaskStatus status,
            Instant dueAt,
            Long sourceRunId,
            String idempotencyKey,
            Instant createdAt) {
        public static TaskResponse from(Task t) {
            return new TaskResponse(t.getId(), t.getTitle(), t.getDescription(),
                    t.getPriority(), t.getStatus(), t.getDueAt(), t.getSourceRunId(),
                    t.getIdempotencyKey(), t.getCreatedAt());
        }
    }

    public record CreateRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 4000) String description,
            TaskPriority priority,
            Instant dueAt) {}

    public record StatusRequest(
            @NotNull TaskStatus status) {}
}
