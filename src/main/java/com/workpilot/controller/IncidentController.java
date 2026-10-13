package com.workpilot.controller;

import com.workpilot.dto.IncidentDtos;
import com.workpilot.entity.Incident;
import com.workpilot.entity.IncidentSeverity;
import com.workpilot.entity.IncidentStatus;
import com.workpilot.service.IncidentService;
import com.workpilot.service.UserContextService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;
    private final UserContextService userContextService;

    /** 条件筛选:startDate/endDate/severity/status 都可选 */
    @GetMapping
    public IncidentDtos.IncidentListResponse list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endDate,
            @RequestParam(required = false) IncidentSeverity severity,
            @RequestParam(required = false) IncidentStatus status) {
        List<Incident> rows = incidentService.search(userContextService.currentUserId(), startDate, endDate, severity, status);
        return new IncidentDtos.IncidentListResponse(
                rows.size(),
                rows.stream().map(IncidentDtos.IncidentResponse::from).toList());
    }

    @GetMapping("/stats")
    public IncidentDtos.StatsResponse stats() {
        return incidentService.stats(userContextService.currentUserId());
    }

    @GetMapping("/{id}")
    public IncidentDtos.IncidentResponse get(@PathVariable Long id) {
        return IncidentDtos.IncidentResponse.from(incidentService.get(userContextService.currentUserId(), id));
    }

    @PostMapping
    public ResponseEntity<IncidentDtos.IncidentResponse> create(@Valid @RequestBody IncidentDtos.CreateRequest req) {
        Incident i = incidentService.create(userContextService.currentUserId(), req);
        return ResponseEntity.status(201).body(IncidentDtos.IncidentResponse.from(i));
    }

    @PatchMapping("/{id}")
    public IncidentDtos.IncidentResponse update(@PathVariable Long id, @RequestBody IncidentDtos.UpdateRequest req) {
        return IncidentDtos.IncidentResponse.from(incidentService.update(userContextService.currentUserId(), id, req));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        incidentService.delete(userContextService.currentUserId(), id);
        return ResponseEntity.noContent().build();
    }
}
