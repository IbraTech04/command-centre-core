package com.ibrasoft.commandcentre.repository;

import com.ibrasoft.commandcentre.model.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    // Newest-first ordering. Id is the tiebreak for events sharing a timestamp
    // (several can land in the same millisecond).

    List<AuditEvent> findAllByOrderByEventTimestampDescIdDesc();

    List<AuditEvent> findByEntityTypeAndEntityIdOrderByEventTimestampDescIdDesc(String entityType, Long entityId);

    List<AuditEvent> findByEventTypeOrderByEventTimestampDescIdDesc(String eventType);

    List<AuditEvent> findByPerformedByOrderByEventTimestampDescIdDesc(String performedBy);

    List<AuditEvent> findByEventTimestampBetweenOrderByEventTimestampDescIdDesc(Instant start, Instant end);
}
