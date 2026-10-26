package com.ibrasoft.commandcentre.service;

import com.ibrasoft.commandcentre.audit.Actor;
import com.ibrasoft.commandcentre.audit.AuditEventType;
import com.ibrasoft.commandcentre.model.AuditEvent;
import com.ibrasoft.commandcentre.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuditEventService {

    static final String REQUEST_ENTITY_TYPE = "Request";

    private final AuditEventRepository auditEventRepository;

    public List<AuditEvent> getAllAuditEvents() {
        return auditEventRepository.findAllByOrderByEventTimestampDescIdDesc();
    }

    public Optional<AuditEvent> getAuditEventById(Long id) {
        return auditEventRepository.findById(id);
    }

    public List<AuditEvent> getAuditEventsByEntityTypeAndId(String entityType, Long entityId) {
        return auditEventRepository.findByEntityTypeAndEntityIdOrderByEventTimestampDescIdDesc(entityType, entityId);
    }

    public List<AuditEvent> getAuditEventsByEventType(String eventType) {
        return auditEventRepository.findByEventTypeOrderByEventTimestampDescIdDesc(eventType);
    }

    public List<AuditEvent> getAuditEventsByPerformedBy(String performedBy) {
        return auditEventRepository.findByPerformedByOrderByEventTimestampDescIdDesc(performedBy);
    }

    public List<AuditEvent> getAuditEventsByDateRange(Instant start, Instant end) {
        return auditEventRepository.findByEventTimestampBetweenOrderByEventTimestampDescIdDesc(start, end);
    }

    /** Convenience overload for Request lifecycle events (no structured metadata). */
    @Transactional
    public AuditEvent logRequestEvent(AuditEventType eventType, Long channelId, String eventDetails, Actor actor) {
        return logEvent(eventType, REQUEST_ENTITY_TYPE, channelId, eventDetails, null, actor);
    }

    /**
     * Persist an audit event for any entity type, with an optional structured JSON {@code metadata}
     * payload alongside the human-readable {@code eventDetails} summary.
     */
    @Transactional
    public AuditEvent logEvent(AuditEventType eventType, String entityType, Long entityId,
                               String eventDetails, String metadataJson, Actor actor) {
        if (eventType == null) {
            throw new IllegalArgumentException("eventType is required for audit logging");
        }
        if (actor == null) {
            throw new IllegalArgumentException("actor is required for audit logging");
        }
        AuditEvent auditEvent = new AuditEvent();
        auditEvent.setEventType(eventType.name());
        auditEvent.setEntityType(entityType != null && !entityType.isBlank() ? entityType : REQUEST_ENTITY_TYPE);
        auditEvent.setEntityId(entityId);
        auditEvent.setEventDetails(eventDetails);
        auditEvent.setMetadata(metadataJson);
        auditEvent.setPerformedBy(actor.format());
        return auditEventRepository.save(auditEvent);
    }
}
