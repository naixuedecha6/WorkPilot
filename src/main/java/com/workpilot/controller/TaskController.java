package com.workpilot.controller;

import com.workpilot.dto.TaskDtos;
import com.workpilot.service.TaskService;
import com.workpilot.service.UserContextService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final UserContextService userContextService;

    @GetMapping
    public List<TaskDtos.TaskResponse> list() {
        return taskService.list(userContextService.currentUserId()).stream()
                .map(TaskDtos.TaskResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<TaskDtos.TaskResponse> create(@Valid @RequestBody TaskDtos.CreateRequest req) {
        TaskDtos.TaskResponse r = TaskDtos.TaskResponse.from(
                taskService.create(userContextService.currentUserId(), req));
        return ResponseEntity.status(201).body(r);
    }

    @PatchMapping("/{id}/status")
    public TaskDtos.TaskResponse updateStatus(@PathVariable Long id,
                                              @Valid @RequestBody TaskDtos.StatusRequest req) {
        return TaskDtos.TaskResponse.from(
                taskService.updateStatus(userContextService.currentUserId(), id, req.status()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        taskService.delete(userContextService.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
