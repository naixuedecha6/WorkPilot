package com.workpilot.tools;

import com.workpilot.dto.ToolContext;
import com.workpilot.dto.ToolResult;
import com.workpilot.entity.Incident;
import com.workpilot.entity.IncidentSeverity;
import com.workpilot.entity.IncidentStatus;
import com.workpilot.repository.IncidentRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 工具层单元测试:不连数据库、不调 LLM,只验证参数解析和返回结构。
 */
class IncidentToolsTest {

    @Test
    void queryIncidentsReturnsRows() {
        IncidentRepository repo = mock(IncidentRepository.class);
        IncidentTools tools = new IncidentTools(repo);

        Incident i = Incident.builder()
                .id(1L).title("测试故障").severity(IncidentSeverity.HIGH)
                .status(IncidentStatus.OPEN).category("BUG")
                .ownerId(2L).occurredAt(Instant.now()).createdAt(Instant.now())
                .build();
        when(repo.search(anyLong(), any(), any(), any(), any())).thenReturn(List.of(i));

        Tool.MapLike args = Args.of(Map.of("limit", 10)).asMapLike();
        ToolContext ctx = ToolContext.builder().userId(2L).build();
        ToolResult r = tools.execute(args, ctx);

        assertTrue(r.isSuccess());
        assertNotNull(r.getData());
        assertEquals(1, r.getData().get("count"));
    }

    @Test
    void queryIncidentsAppliesLimit() {
        IncidentRepository repo = mock(IncidentRepository.class);
        IncidentTools tools = new IncidentTools(repo);
        when(repo.search(anyLong(), any(), any(), any(), any())).thenReturn(List.of());

        // limit=200 应当被钳到 100
        Tool.MapLike args = Args.of(Map.of("limit", 200)).asMapLike();
        ToolContext ctx = ToolContext.builder().userId(2L).build();
        ToolResult r = tools.execute(args, ctx);
        assertTrue(r.isSuccess());
    }
}
