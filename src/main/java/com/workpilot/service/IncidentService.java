package com.workpilot.service;

import com.workpilot.dto.IncidentDtos;
import com.workpilot.entity.Incident;
import com.workpilot.entity.IncidentSeverity;
import com.workpilot.entity.IncidentStatus;
import com.workpilot.exception.AgentException;
import com.workpilot.repository.IncidentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class IncidentService {

    private final IncidentRepository incidentRepository;

    @Transactional(readOnly = true)
    public List<Incident> search(Long userId, Instant startDate, Instant endDate,
                                 IncidentSeverity severity, IncidentStatus status) {
        return incidentRepository.search(userId, startDate, endDate, severity, status);
    }

    @Transactional(readOnly = true)
    public Incident get(Long userId, Long id) {
        Incident i = incidentRepository.findById(id)
                .orElseThrow(() -> new AgentException("INCIDENT_NOT_FOUND", "故障不存在: " + id));
        if (!i.getOwnerId().equals(userId)) {
            throw new AgentException("FORBIDDEN", "无权访问该故障");
        }
        return i;
    }

    @Transactional
    public Incident create(Long userId, IncidentDtos.CreateRequest req) {
        Incident i = Incident.builder()
                .title(req.title())
                .description(req.description())
                .severity(req.severity())
                .status(req.status())
                .category(req.category())
                .occurredAt(req.occurredAt())
                .ownerId(userId)
                .createdAt(Instant.now())
                .build();
        return incidentRepository.save(i);
    }

    @Transactional
    public Incident update(Long userId, Long id, IncidentDtos.UpdateRequest req) {
        Incident i = get(userId, id);
        if (req.title() != null) i.setTitle(req.title());
        if (req.description() != null) i.setDescription(req.description());
        if (req.severity() != null) i.setSeverity(req.severity());
        if (req.status() != null) i.setStatus(req.status());
        if (req.category() != null) i.setCategory(req.category());
        if (req.occurredAt() != null) i.setOccurredAt(req.occurredAt());
        return incidentRepository.save(i);
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Incident i = get(userId, id);
        incidentRepository.delete(i);
    }

    @Transactional(readOnly = true)
    public IncidentDtos.StatsResponse stats(Long userId) {
        List<Incident> all = incidentRepository.search(userId, null, null, null, null);

        Map<IncidentStatus, Integer> byStatus = new LinkedHashMap<>();
        for (IncidentStatus s : IncidentStatus.values()) byStatus.put(s, 0);
        for (Incident i : all) byStatus.merge(i.getStatus(), 1, Integer::sum);

        Map<IncidentSeverity, Integer> bySeverity = new LinkedHashMap<>();
        for (IncidentSeverity s : IncidentSeverity.values()) bySeverity.put(s, 0);
        for (Incident i : all) bySeverity.merge(i.getSeverity(), 1, Integer::sum);

        Map<String, Integer> byCategory = new LinkedHashMap<>();
        for (Incident i : all) byCategory.merge(i.getCategory(), 1, Integer::sum);

        return new IncidentDtos.StatsResponse(
                all.size(),
                byStatus.get(IncidentStatus.OPEN),
                byStatus.get(IncidentStatus.IN_PROGRESS),
                byStatus.get(IncidentStatus.RESOLVED),
                byStatus.get(IncidentStatus.CLOSED),
                bySeverity.entrySet().stream()
                        .map(e -> new IncidentDtos.StatsResponse.GroupCount(e.getKey().name(), e.getValue()))
                        .toList(),
                byCategory.entrySet().stream()
                        .sorted((a, b) -> b.getValue() - a.getValue())
                        .map(e -> new IncidentDtos.StatsResponse.GroupCount(e.getKey(), e.getValue()))
                        .toList());
    }
}
