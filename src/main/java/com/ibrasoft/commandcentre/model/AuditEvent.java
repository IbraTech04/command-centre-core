package com.ibrasoft.commandcentre.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "audit_events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String eventType;

    @NotBlank
    @Column(nullable = false)
    private String entityType;

    @NotNull
    private Long entityId;

    @Size(max = 2000)
    @Column(length = 2000)
    private String eventDetails;

    @NotBlank
    private String performedBy;

    /**
     * Optional structured detail as a JSON string (e.g. {@code {"assigneeIds":["123"],"via":"role"}}).
     * Complements the human-readable {@link #eventDetails} summary. Nullable; snowflake IDs are stored
     * as strings to avoid precision loss on the JS frontend.
     */
    @Column(columnDefinition = "TEXT")
    private String metadata;

    /**
     * The moment the event happened, as an absolute instant. Stored in a {@code timestamp}
     * column as UTC (see {@code hibernate.jdbc.time_zone}) and serialized as ISO-8601 with a
     * {@code Z} suffix, so clients never have to guess which zone the value is in.
     */
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(nullable = false)
    private Instant eventTimestamp;

    @PrePersist
    protected void onCreate() {
        eventTimestamp = Instant.now();
    }
}
