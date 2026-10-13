package com.workpilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 单步执行结果,在 AgentRun 内累积,最终汇总为运行结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StepResult {
    private String stepId;
    private String tool;
    private boolean success;
    private Map<String, Object> data;   // 结构化结果(供后续步骤使用)
    private String summary;              // 文字摘要(供人阅读)
    private String errorCode;
    private String errorMessage;
}
