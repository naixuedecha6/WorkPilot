package com.workpilot.tools;

import com.workpilot.dto.ToolContext;
import com.workpilot.dto.ToolResult;
import com.workpilot.entity.Task;
import com.workpilot.entity.TaskPriority;
import com.workpilot.entity.TaskStatus;
import com.workpilot.exception.AgentException;
import com.workpilot.repository.TaskRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * 创建跟进待办 — 唯一会触发审批的写操作工具。
 *
 * <p>幂等键:由 (runId, stepId) 组合生成,保证同一 Agent 运行内重复提交只产生一条待办。
 * 即使审批被点击多次,只要参数和幂等键一致,只创建一次。</p>
 */
@Component
public class TaskTools implements Tool {

    private final TaskRepository taskRepository;

    public TaskTools(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Override public String name() { return "create_task"; }

    @Override
    public String description() {
        return "创建一条跟进待办。写操作,必须经过审批。参数:" +
                "title(必填), description(可选), priority(LOW/MEDIUM/HIGH/URGENT,默认MEDIUM), " +
                "dueInDays(整数,几天后到期,默认7)。";
    }

    @Override
    public boolean requiresApproval() {
        return true;
    }

    @Override
    public ToolResult execute(MapLike arguments, ToolContext context) {
        String title = arguments.getString("title");
        if (title == null || title.isBlank()) {
            return ToolResult.fail("INVALID_ARGS", "title 必填");
        }
        if (title.length() > 200) {
            return ToolResult.fail("INVALID_ARGS", "title 过长(>200)");
        }
        String description = arguments.getString("description");
        TaskPriority priority = arguments.getEnum("priority", TaskPriority.class, TaskPriority.MEDIUM);
        int dueInDays = arguments.getInt("dueInDays", 7);
        if (dueInDays < 0 || dueInDays > 365) dueInDays = 7;

        // 幂等键:同一 run + step 内创建同 title 的待办视为重复
        String idempotencyKey = "run-" + context.getRunId()
                + "-title-" + title.hashCode();

        // 二次校验:即便 Agent 反复请求,也只创建一次
        var existing = taskRepository.findByCreatorIdAndIdempotencyKey(context.getUserId(), idempotencyKey);
        if (existing.isPresent()) {
            return ToolResult.ok(Map.of(
                    "taskId", existing.get().getId(),
                    "title", existing.get().getTitle(),
                    "idempotent", true
            ), "已存在相同待办(幂等),taskId=" + existing.get().getId());
        }

        Task task = Task.builder()
                .creatorId(context.getUserId())
                .title(title)
                .description(description)
                .priority(priority)
                .status(TaskStatus.OPEN)
                .dueAt(Instant.now().plus(dueInDays, ChronoUnit.DAYS))
                .sourceRunId(context.getRunId())
                .idempotencyKey(idempotencyKey)
                .createdAt(Instant.now())
                .build();
        try {
            task = taskRepository.save(task);
        } catch (Exception e) {
            // 唯一约束冲突 = 已经被并发创建,返回已有记录
            if (e.getMessage() != null && e.getMessage().contains("uq_task_idem")) {
                var again = taskRepository.findByCreatorIdAndIdempotencyKey(context.getUserId(), idempotencyKey);
                if (again.isPresent()) {
                    return ToolResult.ok(Map.of(
                            "taskId", again.get().getId(),
                            "title", again.get().getTitle(),
                            "idempotent", true
                    ), "并发创建已合并");
                }
            }
            throw new AgentException("TASK_CREATE_FAILED", e.getMessage());
        }

        return ToolResult.ok(Map.of(
                "taskId", task.getId(),
                "title", task.getTitle(),
                "priority", task.getPriority().name(),
                "dueAt", task.getDueAt() == null ? "" : task.getDueAt().toString()
        ), "已创建待办: " + task.getTitle());
    }
}
