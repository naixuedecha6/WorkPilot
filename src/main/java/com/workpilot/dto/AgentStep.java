package com.workpilot.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Agent 单个步骤的结构化定义(对应文档 §5.2)。
 * 工具名称必须从服务端允许列表中选取,不能由模型自由发挥。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AgentStep {
    @JsonProperty("id")
    private String id;

    @JsonProperty("tool")
    private String tool;

    @JsonProperty("purpose")
    private String purpose;

    @JsonProperty("arguments")
    private Map<String, Object> arguments;

    @JsonProperty("requiresApproval")
    private Boolean requiresApproval;
}
