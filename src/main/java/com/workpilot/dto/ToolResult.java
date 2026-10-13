package com.workpilot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 工具执行结果。所有工具实现必须返回该结构。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {
    private boolean success;
    private Map<String, Object> data;   // 机器可读的结构化结果
    private String summary;            // 给人看的摘要
    private String errorCode;          // 失败时的错误码
    private String errorMessage;       // 失败时的可读消息
    private boolean requiresApproval;  // 写操作时为 true

    public static ToolResult ok(Map<String, Object> data, String summary) {
        return ToolResult.builder().success(true).data(data).summary(summary).build();
    }

    public static ToolResult fail(String code, String msg) {
        return ToolResult.builder().success(false).errorCode(code).errorMessage(msg).build();
    }
}
