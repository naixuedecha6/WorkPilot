package com.workpilot.tools;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 工具注册表:管理所有可用工具,提供白名单查询。
 *
 * <p>这是模型与实际业务能力之间的"单向门"——模型只能选取注册表里的工具,
 * 不能注册新工具,不能调用未列出的方法。</p>
 */
@Component
public class ToolRegistry {

    private final Map<String, Tool> tools = new HashMap<>();

    public ToolRegistry(List<Tool> toolBeans) {
        for (Tool t : toolBeans) {
            tools.put(t.name(), t);
        }
    }

    public Optional<Tool> get(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Set<String> names() {
        return tools.keySet();
    }

    /** 工具描述列表(给模型 Prompt 用) */
    public List<Map<String, String>> describeAll() {
        return tools.values().stream()
                .map(t -> Map.of(
                        "name", t.name(),
                        "description", t.description(),
                        "requiresApproval", String.valueOf(t.requiresApproval())))
                .collect(Collectors.toList());
    }
}
