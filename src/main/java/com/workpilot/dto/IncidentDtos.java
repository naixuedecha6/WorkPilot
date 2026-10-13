package com.workpilot.dto;

import com.workpilot.entity.Incident;
import com.workpilot.entity.IncidentSeverity;
import com.workpilot.entity.IncidentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class IncidentDtos {

    private IncidentDtos() {}

    public record IncidentResponse(
            Long id,
            String title,
            String description,
            IncidentSeverity severity,
            IncidentStatus status,
            String category,
            Instant occurredAt,
            Instant createdAt) {

        public static IncidentResponse from(Incident i) {
            return new IncidentResponse(
                    i.getId(), i.getTitle(), i.getDescription(),
                    i.getSeverity(), i.getStatus(), i.getCategory(),
                    i.getOccurredAt(), i.getCreatedAt());
        }
    }

    public record IncidentListResponse(
            int total,
            List<IncidentResponse> rows) {}

    public record CreateRequest(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 4000) String description,
            @NotNull IncidentSeverity severity,
            @NotNull IncidentStatus status,
            @NotBlank @Size(max = 64) String category,
            @NotNull Instant occurredAt) {}

    public record UpdateRequest(
            @Size(max = 255) String title,
            @Size(max = 4000) String description,
            IncidentSeverity severity,
            IncidentStatus status,
            @Size(max = 64) String category,
            Instant occurredAt) {}

    /** 严重度 / 类别 / 状态 三组计数 */
    public record StatsResponse(
            int total,
            int open,
            int inProgress,
            int resolved,
            int closed,
            List<GroupCount> bySeverity,
            List<GroupCount> byCategory) {

        public record GroupCount(String key, int count) {}
    }
}
