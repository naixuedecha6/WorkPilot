package com.workpilot.entity;

/**
 * Agent 状态机:严格按文档 §7.2 的状态迁移图。
 *
 * <pre>
 * CREATED → PLANNING → RUNNING → COMPLETED
 *                  │       │  → WAITING_APPROVAL → RUNNING → COMPLETED
 *                  │       │                          ↘ REJECTED
 *                  │       ↘ FAILED
 *                  ↘ FAILED(规划阶段失败)
 * </pre>
 */
public enum AgentStatus {
    CREATED, PLANNING, RUNNING, WAITING_APPROVAL, COMPLETED, FAILED, REJECTED, CANCELLED
}
