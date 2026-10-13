package com.workpilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 工具上下文:每次执行时传给工具实现,包含用户身份和当前运行信息。
 * 工具不直接访问 HttpSession / SecurityContext,完全通过此对象获取必要信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolContext {
    private Long userId;
    private String username;
    private Long runId;
    private Long conversationId;
    private List<StepResult> previousResults;  // 前面步骤的累积结果

    /** 把前置步骤的 data 合并成扁平 Map,供后续步骤读(同名 key 后写覆盖) */
    public Map<String, Object> flatPreviousData() {
        java.util.LinkedHashMap<String, Object> flat = new java.util.LinkedHashMap<>();
        if (previousResults == null) return flat;
        for (StepResult r : previousResults) {
            if (r.getData() != null) flat.putAll(r.getData());
        }
        return flat;
    }
}
