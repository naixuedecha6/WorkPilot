package com.workpilot.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "approvals")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Approval {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "tool_execution_id", nullable = false)
    private Long toolExecutionId;

    @Column(name = "tool_name", nullable = false, length = 64)
    private String toolName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ApprovalStatus status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
