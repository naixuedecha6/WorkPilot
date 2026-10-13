package com.workpilot.tools;

import com.workpilot.dto.ToolContext;
import com.workpilot.dto.ToolResult;

/**
 * 所有业务工具实现此接口。
 *
 * <p>设计原则(对应文档 §9.2):</p>
 * <ul>
 *   <li>一个工具只完成一个边界清晰的业务动作</li>
 *   <li>参数与返回值结构稳定,错误可识别</li>
 *   <li>不暴露任意 SQL/Shell/文件系统</li>
 *   <li>副作用工具(requiresApproval=true)必须由 AgentPolicy 强制走审批</li>
 * </ul>
 */
public interface Tool {
    /** 工具名称,必须与服务端白名单一致 */
    String name();

    /** 工具的简短描述,供模型在生成计划时理解 */
    String description();

    /** 是否会产生副作用(写操作),true 则必须经审批才能执行 */
    default boolean requiresApproval() {
        return false;
    }

    /** 执行业务逻辑,返回结构化结果 */
    ToolResult execute(MapLike arguments, ToolContext context);

    /**
     * 简化的 Map-like 访问接口,避免在工具实现里到处写类型转换。
     */
    interface MapLike {
        String getString(String key);
        Integer getInt(String key, Integer defaultValue);
        Long getLong(String key, Long defaultValue);
        java.time.Instant getInstant(String key);
        <T extends Enum<T>> T getEnum(String key, Class<T> type, T defaultValue);
        java.util.List<MapLike> getList(String key);
        boolean contains(String key);
    }
}
