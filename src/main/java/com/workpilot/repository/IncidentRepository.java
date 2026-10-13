package com.workpilot.repository;

import com.workpilot.entity.Incident;
import com.workpilot.entity.IncidentSeverity;
import com.workpilot.entity.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface IncidentRepository extends JpaRepository<Incident, Long> {

    @Query("""
        SELECT i FROM Incident i
        WHERE i.ownerId = :userId
          AND (:startDate IS NULL OR i.occurredAt >= :startDate)
          AND (:endDate IS NULL OR i.occurredAt < :endDate)
          AND (:severity IS NULL OR i.severity = :severity)
          AND (:status IS NULL OR i.status = :status)
        ORDER BY i.occurredAt DESC
        """)
    List<Incident> search(@Param("userId") Long userId,
                          @Param("startDate") Instant startDate,
                          @Param("endDate") Instant endDate,
                          @Param("severity") IncidentSeverity severity,
                          @Param("status") IncidentStatus status);

    @Query("SELECT i.severity AS severity, COUNT(i) AS count FROM Incident i " +
           "WHERE i.ownerId = :userId GROUP BY i.severity")
    List<Object[]> countBySeverity(@Param("userId") Long userId);

    @Query("SELECT i.category AS category, COUNT(i) AS count FROM Incident i " +
           "WHERE i.ownerId = :userId GROUP BY i.category ORDER BY COUNT(i) DESC")
    List<Object[]> countByCategory(@Param("userId") Long userId);
}
