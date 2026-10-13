package com.workpilot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workpilot.agent.AgentOrchestrator;
import com.workpilot.dto.AgentDtos;
import com.workpilot.dto.ReportDtos;
import com.workpilot.entity.AgentRun;
import com.workpilot.entity.ToolExecution;
import com.workpilot.exception.AgentException;
import com.workpilot.repository.AgentRunRepository;
import com.workpilot.repository.ToolExecutionRepository;
import com.workpilot.service.ChatService;
import com.workpilot.service.UserContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentTaskController {

    private final AgentOrchestrator orchestrator;
    private final AgentRunRepository runRepository;
    private final ToolExecutionRepository executionRepository;
    private final ChatService chatService;
    private final UserContextService userContextService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 创建一个 Agent 运行 */
    @PostMapping("/runs")
    public Map<String, Object> createRun(@RequestBody Map<String, Object> body) {
        String input = String.valueOf(body.getOrDefault("input", ""));
        Long conversationId = body.get("conversationId") == null ? null
                : Long.valueOf(body.get("conversationId").toString());
        if (conversationId == null) {
            String title = input.length() > 30 ? input.substring(0, 30) : input;
            conversationId = chatService.createConversation(title);
        }
        chatService.saveUserMessage(conversationId, input);
        AgentRun run = orchestrator.start(input, userContextService.currentUserId(), conversationId);
        chatService.saveAssistantMessage(conversationId,
                "Agent 任务状态: " + run.getStatus() + (run.getOutput() == null ? "" : "\n\n" + run.getOutput()),
                run.getId());
        return Map.of(
                "runId", run.getId(),
                "conversationId", conversationId,
                "status", run.getStatus().name()
        );
    }

    /** 当前用户的所有运行 */
    @GetMapping("/runs")
    public List<AgentDtos.RunSummary> listRuns() {
        return runRepository.findByUserIdOrderByCreatedAtDesc(userContextService.currentUserId())
                .stream().map(this::toSummary).toList();
    }

    /** 查询运行 */
    @GetMapping("/runs/{id}")
    public AgentDtos.RunSummary getRun(@PathVariable Long id) {
        return toSummary(loadOwnedRun(id));
    }

    /** 查询执行轨迹(含 input/output/plan/evidence) */
    @GetMapping("/runs/{id}/trace")
    public AgentDtos.TraceResponse getTrace(@PathVariable Long id) {
        AgentRun run = loadOwnedRun(id);
        List<ToolExecution> execs = executionRepository.findByRunIdOrderByCreatedAtAsc(id);
        List<AgentDtos.ExecutionDto> execDtos = new ArrayList<>();
        List<AgentDtos.Citation> citations = new ArrayList<>();
        for (ToolExecution e : execs) {
            Map<String, Object> data = null;
            String summary = null;
            if (e.getResult() != null) {
                try {
                    Map<?, ?> m = objectMapper.readValue(e.getResult(), Map.class);
                    Object s = m.get("summary");
                    if (s != null) summary = s.toString();
                    Object d = m.get("data");
                    if (d instanceof Map<?, ?> dm) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> typed = (Map<String, Object>) dm;
                        data = typed;
                    }
                } catch (Exception ignored) { }
            }
            execDtos.add(new AgentDtos.ExecutionDto(
                    e.getId(), e.getStepId(), e.getToolName(), e.getStatus().name(),
                    e.getArguments(), e.getDurationMs(), e.getErrorCode(), summary, data));
            // 抽取 search_knowledge 的命中片段
            if ("search_knowledge".equals(e.getToolName()) && e.getStatus().name().equals("SUCCESS") && data != null) {
                extractCitations(data, citations);
            }
        }

        Object planObj = parseJsonSafely(run.getPlan());
        return new AgentDtos.TraceResponse(
                toSummary(run),
                run.getPlan(),
                planObj,
                run.getOutput(),
                run.getErrorMessage(),
                execDtos,
                citations);
    }

    @SuppressWarnings("unchecked")
    private void extractCitations(Map<String, Object> data, List<AgentDtos.Citation> out) {
        Object hitsObj = data.get("hits");
        if (!(hitsObj instanceof List<?> hits)) return;
        for (Object h : hits) {
            if (!(h instanceof Map<?, ?> hm)) continue;
            Object content = hm.get("content");
            Object meta = hm.get("metadata");
            String docName = null;
            Integer chunkIdx = null;
            if (meta instanceof Map<?, ?> mm) {
                Object n = mm.get("document_name");
                if (n != null) docName = n.toString();
                Object ci = mm.get("chunk_index");
                if (ci != null) {
                    try { chunkIdx = Integer.valueOf(ci.toString()); } catch (Exception ignored) { }
                }
            }
            Double score = null;
            Object s = hm.get("score");
            if (s != null) {
                try { score = Double.valueOf(s.toString()); } catch (Exception ignored) { }
            }
            out.add(new AgentDtos.Citation(
                    docName, chunkIdx, score,
                    content == null ? "" : content.toString()));
        }
    }

    private AgentRun loadOwnedRun(Long id) {
        AgentRun run = runRepository.findById(id)
                .orElseThrow(() -> new AgentException("RUN_NOT_FOUND", "运行不存在: " + id));
        if (!run.getUserId().equals(userContextService.currentUserId())) {
            throw new AgentException("FORBIDDEN", "无权访问该运行");
        }
        return run;
    }

    private AgentDtos.RunSummary toSummary(AgentRun r) {
        return new AgentDtos.RunSummary(
                r.getId(),
                r.getStatus().name(),
                r.getInput(),
                r.getOutput(),
                r.getErrorMessage(),
                r.getStartedAt() == null ? null : r.getStartedAt().toString(),
                r.getFinishedAt() == null ? null : r.getFinishedAt().toString(),
                r.getCreatedAt() == null ? null : r.getCreatedAt().toString());
    }

    private Object parseJsonSafely(String json) {
        if (json == null || json.isBlank()) return null;
        try { return objectMapper.readValue(json, Object.class); }
        catch (Exception e) { return null; }
    }
}
