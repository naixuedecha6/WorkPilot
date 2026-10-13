package com.workpilot.tools;

import com.workpilot.dto.ToolContext;
import com.workpilot.dto.ToolResult;
import com.workpilot.entity.Incident;
import com.workpilot.entity.IncidentSeverity;
import com.workpilot.entity.IncidentStatus;
import com.workpilot.repository.IncidentRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 故障记录查询:结构化参数 + 固定查询;不允许任意 SQL。
 */
@Component
public class IncidentTools implements Tool {

    private final IncidentRepository incidentRepository;

    public IncidentTools(IncidentRepository incidentRepository) {
        this.incidentRepository = incidentRepository;
    }

    @Override public String name() { return "query_incidents"; }

    @Override
    public String description() {
        return "按条件查询故障记录。只读工具。参数:" +
                "startDate(ISO-8601,可选), endDate(ISO-8601,可选), " +
                "severity(LOW/MEDIUM/HIGH/CRITICAL,可选), status(OPEN/IN_PROGRESS/RESOLVED/CLOSED,可选), " +
                "limit(1-100,默认20)。" +
                "无审批。";
    }

    @Override
    public ToolResult execute(MapLike arguments, ToolContext context) {
        Long userId = context.getUserId();
        Instant start = arguments.getInstant("startDate");
        Instant end = arguments.getInstant("endDate");
        IncidentSeverity severity = arguments.getEnum("severity", IncidentSeverity.class, null);
        IncidentStatus status = arguments.getEnum("status", IncidentStatus.class, null);
        int limit = Math.min(Math.max(arguments.getInt("limit", 20), 1), 100);

        if (start == null) start = Instant.now().minus(30, ChronoUnit.DAYS);
        if (end == null) end = Instant.now().plus(1, ChronoUnit.DAYS);

        List<Incident> all = incidentRepository.search(userId, start, end, severity, status);
        List<Incident> top = all.subList(0, Math.min(limit, all.size()));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Incident i : top) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("id", i.getId());
            r.put("title", i.getTitle());
            r.put("severity", i.getSeverity().name());
            r.put("status", i.getStatus().name());
            r.put("category", i.getCategory());
            r.put("occurredAt", i.getOccurredAt().toString());
            r.put("description", i.getDescription() == null ? "" : i.getDescription());
            rows.add(r);
        }

        String summary = "查询到 " + all.size() + " 条故障(返回前 " + rows.size() + " 条)";
        return ToolResult.ok(Map.of(
                "count", all.size(),
                "rows", rows,
                "startDate", start.toString(),
                "endDate", end.toString()
        ), summary);
    }
}
