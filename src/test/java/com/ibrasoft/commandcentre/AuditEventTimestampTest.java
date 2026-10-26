package com.ibrasoft.commandcentre;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.commandcentre.model.AuditEvent;
import com.ibrasoft.commandcentre.repository.AuditEventRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the audit timestamp contract: stored as an absolute instant, returned to clients as
 * ISO-8601 with an explicit UTC offset. A zone-less value here is what made the dashboard
 * render events in the API host's wall-clock time instead of the viewer's.
 */
@SpringBootTest
class AuditEventTimestampTest {

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @Transactional
    void timestampSurvivesTheRoundTripToTheDatabase() {
        Instant before = Instant.now().truncatedTo(ChronoUnit.SECONDS);

        AuditEvent event = new AuditEvent();
        event.setEventType("TEST_EVENT");
        event.setEntityType("Request");
        event.setEntityId(1L);
        event.setEventDetails("timezone round-trip");
        event.setPerformedBy("bot:test");
        AuditEvent saved = auditEventRepository.saveAndFlush(event);

        // Drop the persistence context so the value really comes back off the connection.
        entityManager.clear();
        AuditEvent reloaded = auditEventRepository.findById(saved.getId()).orElseThrow();

        // Compared at millisecond precision: SQLite (dev) keeps millis, Postgres (prod) micros.
        assertEquals(saved.getEventTimestamp().truncatedTo(ChronoUnit.MILLIS),
            reloaded.getEventTimestamp().truncatedTo(ChronoUnit.MILLIS));
        assertTrue(!reloaded.getEventTimestamp().isBefore(before)
                && reloaded.getEventTimestamp().isBefore(before.plus(1, ChronoUnit.HOURS)),
            "stored timestamp drifted from the current instant: " + reloaded.getEventTimestamp());
    }

    @Test
    void timestampSerializesAsUtcIso8601() throws Exception {
        AuditEvent event = new AuditEvent();
        event.setEventTimestamp(Instant.parse("2026-08-27T19:45:16.572993Z"));

        assertTrue(objectMapper.writeValueAsString(event).contains("\"eventTimestamp\":\"2026-08-27T19:45:16.572993Z\""),
            "audit timestamps must carry an explicit UTC offset so clients cannot read them as local time");
    }
}
