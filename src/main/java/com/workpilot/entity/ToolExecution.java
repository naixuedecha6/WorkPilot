package com.workpilot.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "tool_executions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ToolExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "step_id", nullable = false, length = 64)
    private String stepId;

    @Column(name = "tool_name", nullable = false, length = 64)
    private String toolName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ToolExecutionStatus status;

    @Column(columnDefinition = "TEXT")
    private String arguments;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
