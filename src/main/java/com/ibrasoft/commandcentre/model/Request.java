package com.ibrasoft.commandcentre.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Request {

    @Id
    @NotNull
    private Long channelID;

    /**
     * DISCORD RELATED FIELDS
     *
     * This includes stuff like channel IDs, message IDs, guild IDs, etc.
     */

    private Long requesterID;

    private Long requesterDepartmentID;

    private Long assignedToID;

    @Column(name = "additionalAsigneeID")
    private Long additionalAssigneeID;

    @NotNull
    @Column(nullable = false)
    private Long mainMessageID;

    /**
     * MARKETING REQUEST RELATED FIELDS
     *
     * Everything else related to the request, but not directly related to Discord
     */

    @Size(max = 255)
    private String title;

    @Size(max = 4000)
    @Column(length = 4000)
    private String description;

    @Enumerated(EnumType.STRING)
    private RequestType requestType;

    @Enumerated(EnumType.STRING)
    private RequestStatus status;

    private LocalDate postingDate;

    /**
     * Absolute instants, stored in {@code timestamp} columns as UTC (see
     * {@code hibernate.jdbc.time_zone}) and serialized as ISO-8601 with a {@code Z} suffix.
     * {@link #postingDate} stays a {@link LocalDate} - it is a calendar date, not a moment.
     */
    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(nullable = false)
    private Instant updatedAt;

    @Size(max = 255)
    @Column(length = 255, nullable = true)
    private String room;

    @Size(max = 255)
    @Column(length = 255, nullable = true)
    private String signupUrl;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
