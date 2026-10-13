package com.workpilot.tools;

import com.workpilot.dto.ToolContext;
import com.workpilot.dto.ToolResult;
import com.workpilot.repository.IncidentRepository;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 故障统计:按严重程度、分类聚合,生成高频问题列表。
 */
@Component
public class IncidentSummaryTools implements Tool {

    private final IncidentRepository incidentRepository;

    public IncidentSummaryTools(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    @Override public String name() { return "summarize_incidents"; }

    @Override
    public String description() {
        return "统计故障记录:按严重程度和分类聚合,返回高频问题。无需参数。无需审批。";
    }

    @Override
    public ToolResult execute(MapLike arguments, ToolContext context) {
        Long userId = context.getUserId();
        List<Object[]> bySev = incidentRepository.countBySeverity(userId);
        List<Object[]> byCat = incidentRepository.countByCategory(userId);

        Map<String, Long> severity = new LinkedHashMap<>();
        for (Object[] row : bySev) {
            severity.put(((Enum<?>) row[0]).name(), ((Number) row[1]).longValue());
        }
        List<Map<String, Object>> categories = new ArrayList<>();
        for (Object[] row : byCat) {
            categories.add(Map.of(
                    "category", String.valueOf(row[0]),
                    "count", ((Number) row[1]).longValue()
            ));
        }
        long total = severity.values().stream().mapToLong(Long::longValue).sum();

        String summary = "共 " + total + " 条故障,严重程度分布: " + severity;
        return ToolResult.ok(Map.of(
                "total", total,
                "bySeverity", severity,
                "byCategory", categories
        ), summary);
    }
}
