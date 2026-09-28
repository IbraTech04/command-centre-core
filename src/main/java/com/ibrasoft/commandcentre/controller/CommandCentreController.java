package com.ibrasoft.commandcentre.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibrasoft.commandcentre.audit.Actor;
import com.ibrasoft.commandcentre.audit.AuditEventType;
import com.ibrasoft.commandcentre.audit.AuthenticationActorResolver;
import com.ibrasoft.commandcentre.audit.AuthenticationActorResolver.ActorResolutionException;
import com.ibrasoft.commandcentre.controller.dto.AuditEventRequest;
import com.ibrasoft.commandcentre.model.AuditEvent;
import com.ibrasoft.commandcentre.model.DepartmentCount;
import com.ibrasoft.commandcentre.model.Request;
import com.ibrasoft.commandcentre.model.RequestStatus;
import com.ibrasoft.commandcentre.service.AuditEventService;
import com.ibrasoft.commandcentre.service.RequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;


@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CommandCentreController {

    private static final String ON_BEHALF_OF_HEADER = "X-Discord-User-Id";

    private final RequestService requestService;
    private final AuditEventService auditEventService;
    private final AuthenticationActorResolver actorResolver;
    private final ObjectMapper objectMapper;

    // ========== Request Endpoints ==========

    @GetMapping("/requests")
    public ResponseEntity<List<Request>> getAllRequests() {
        return ResponseEntity.ok(requestService.getAllRequests());
    }

    @GetMapping("/requests/channel/{channelId}")
    public ResponseEntity<Request> getRequestByChannelId(@PathVariable Long channelId) {
        return requestService.getRequestByChannelId(channelId)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/requests/status/{status}")
    public ResponseEntity<List<Request>> getRequestsByStatus(@PathVariable RequestStatus status) {
        return ResponseEntity.ok(requestService.getRequestsByStatus(status));
    }

    @GetMapping("/requests/requester/{requesterId}")
    public ResponseEntity<List<Request>> getRequestsByRequester(@PathVariable Long requesterId) {
        return ResponseEntity.ok(requestService.getRequestsByRequester(requesterId));
    }

    @GetMapping("/requests/assigned/{assignedToId}")
    public ResponseEntity<List<Request>> getRequestsByAssignedTo(@PathVariable Long assignedToId) {
        return ResponseEntity.ok(requestService.getRequestsByAssignedTo(assignedToId));
    }

    @GetMapping("/requests/my-requests")
    public ResponseEntity<List<Request>> getMyRequests(Authentication authentication) {
        Actor actor;
        try {
            actor = actorResolver.resolve(authentication, null);
        } catch (ActorResolutionException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (actor.kind() != Actor.Kind.USER) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(requestService.getRequestsByRequester(actor.discordUserId()));
    }

    @PostMapping("/requests")
    public ResponseEntity<Request> createRequest(
            @Valid @RequestBody Request request,
            @RequestHeader(value = ON_BEHALF_OF_HEADER, required = false) Long onBehalfOfUserId,
            Authentication authentication) {
        Actor actor;
        try {
            Long obo = onBehalfOfUserId != null ? onBehalfOfUserId : request.getRequesterID();
            actor = actorResolver.resolve(authentication, actorResolver.isBot(authentication) ? obo : null);
        } catch (ActorResolutionException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (actorResolver.isBot(authentication) && request.getRequesterID() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
        Request createdRequest = requestService.createRequest(request, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdRequest);
    }

    @PutMapping("/requests/channel/{channelId}")
    public ResponseEntity<Request> updateRequest(
            @PathVariable Long channelId,
            @Valid @RequestBody Request request,
            @RequestHeader(value = ON_BEHALF_OF_HEADER, required = false) Long onBehalfOfUserId,
            Authentication authentication) {
        Actor actor;
        try {
            actor = actorResolver.resolve(authentication, onBehalfOfUserId);
        } catch (ActorResolutionException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            return ResponseEntity.ok(requestService.updateRequest(channelId, request, actor));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/requests/channel/{channelId}")
    public ResponseEntity<Void> deleteRequest(
            @PathVariable Long channelId,
            @RequestHeader(value = ON_BEHALF_OF_HEADER, required = false) Long onBehalfOfUserId,
            Authentication authentication) {
        Actor actor;
        try {
            actor = actorResolver.resolve(authentication, onBehalfOfUserId);
        } catch (ActorResolutionException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            requestService.deleteRequest(channelId, actor);
            return ResponseEntity.noContent().build();
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/requests/channel/{channelId}/assign/{assignedToId}")
    public ResponseEntity<Request> assignRequest(
            @PathVariable Long channelId,
            @PathVariable Long assignedToId,
            @RequestHeader(value = ON_BEHALF_OF_HEADER, required = false) Long onBehalfOfUserId,
            Authentication authentication) {
        Actor actor;
        try {
            actor = actorResolver.resolve(authentication, onBehalfOfUserId);
        } catch (ActorResolutionException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            return ResponseEntity.ok(requestService.assignRequest(channelId, assignedToId, actor));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/requests/channel/{channelId}/status/{status}")
    public ResponseEntity<?> setRequestStatus(
            @PathVariable Long channelId,
            @PathVariable String status,
            @RequestHeader(value = ON_BEHALF_OF_HEADER, required = false) Long onBehalfOfUserId,
            Authentication authentication) {
        Actor actor;
        try {
            actor = actorResolver.resolve(authentication, onBehalfOfUserId);
        } catch (ActorResolutionException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            RequestStatus requestStatus = RequestStatus.valueOf(status.toUpperCase());
            return ResponseEntity.ok(requestService.setRequestStatus(channelId, requestStatus, actor));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status: " + status);
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/requests/channel/{channelId}/advance")
    public ResponseEntity<?> advanceRequestToNextStatus(
            @PathVariable Long channelId,
            @RequestHeader(value = ON_BEHALF_OF_HEADER, required = false) Long onBehalfOfUserId,
            Authentication authentication) {
        Actor actor;
        try {
            actor = actorResolver.resolve(authentication, onBehalfOfUserId);
        } catch (ActorResolutionException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            return ResponseEntity.ok(requestService.advanceRequestToNextStatus(channelId, actor));
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/requests/channel/{channelId}/department/{departmentId}")
    public ResponseEntity<Request> updateRequesterDepartment(
            @PathVariable Long channelId,
            @PathVariable Long departmentId,
            @RequestHeader(value = ON_BEHALF_OF_HEADER, required = false) Long onBehalfOfUserId,
            Authentication authentication) {
        Actor actor;
        try {
            actor = actorResolver.resolve(authentication, onBehalfOfUserId);
        } catch (ActorResolutionException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            return ResponseEntity.ok(requestService.updateRequesterDepartment(channelId, departmentId, actor));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/requests/channel/{channelId}/requester/{requesterId}")
    public ResponseEntity<Request> updateRequester(
            @PathVariable Long channelId,
            @PathVariable Long requesterId,
            @RequestHeader(value = ON_BEHALF_OF_HEADER, required = false) Long onBehalfOfUserId,
            Authentication authentication) {
        Actor actor;
        try {
            actor = actorResolver.resolve(authentication, onBehalfOfUserId);
        } catch (ActorResolutionException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            return ResponseEntity.ok(requestService.updateRequester(channelId, requesterId, actor));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/requests/countByDepartment")
    public ResponseEntity<List<DepartmentCount>> countRequestsByDepartment(Authentication authentication) {
        try {
            actorResolver.resolve(authentication, null);
        } catch (ActorResolutionException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            return ResponseEntity.ok(requestService.getRequestCountsByDepartment());
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }


    // ========== Audit Event Endpoints ==========

    @PostMapping("/audit-events")
    public ResponseEntity<?> createAuditEvent(
            @RequestBody AuditEventRequest body,
            @RequestHeader(value = ON_BEHALF_OF_HEADER, required = false) Long onBehalfOfUserId,
            Authentication authentication) {
        Actor actor;
        try {
            actor = actorResolver.resolve(authentication, onBehalfOfUserId);
        } catch (ActorResolutionException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (body == null || body.entityId() == null) {
            return ResponseEntity.badRequest().body("entityId is required");
        }
        AuditEventType eventType = AuditEventType.fromString(body.eventType()).orElse(null);
        if (eventType == null) {
            return ResponseEntity.badRequest().body("Invalid eventType: " + body.eventType());
        }

        String metadataJson = null;
        JsonNode metadata = body.metadata();
        if (metadata != null && !metadata.isNull()) {
            metadataJson = metadata.toString();
        }

        AuditEvent saved = auditEventService.logEvent(
            eventType, body.entityType(), body.entityId(), body.eventDetails(), metadataJson, actor);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping("/audit-events")
    public ResponseEntity<List<AuditEvent>> getAllAuditEvents() {
        return ResponseEntity.ok(auditEventService.getAllAuditEvents());
    }

    @GetMapping("/audit-events/{id}")
    public ResponseEntity<AuditEvent> getAuditEventById(@PathVariable Long id) {
        return auditEventService.getAuditEventById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/audit-events/entity/{entityType}/{entityId}")
    public ResponseEntity<List<AuditEvent>> getAuditEventsByEntity(
            @PathVariable String entityType,
            @PathVariable Long entityId) {
        return ResponseEntity.ok(auditEventService.getAuditEventsByEntityTypeAndId(entityType, entityId));
    }

    @GetMapping("/audit-events/type/{eventType}")
    public ResponseEntity<List<AuditEvent>> getAuditEventsByType(@PathVariable String eventType) {
        return ResponseEntity.ok(auditEventService.getAuditEventsByEventType(eventType));
    }

    @GetMapping("/audit-events/user/{performedBy}")
    public ResponseEntity<List<AuditEvent>> getAuditEventsByUser(@PathVariable String performedBy) {
        return ResponseEntity.ok(auditEventService.getAuditEventsByPerformedBy(performedBy));
    }

    @GetMapping("/audit-events/daterange")
    public ResponseEntity<List<AuditEvent>> getAuditEventsByDateRange(
            @RequestParam String start,
            @RequestParam String end) {
        return ResponseEntity.ok(auditEventService.getAuditEventsByDateRange(parseInstant(start), parseInstant(end)));
    }

    /**
     * Parse an ISO-8601 bound for the audit date-range query. Values carrying an offset
     * ("2026-08-27T19:45:16Z", "...-04:00") are honoured as-is; a bare local date-time is read
     * as UTC, matching how audit timestamps are stored and returned.
     */
    private static Instant parseInstant(String value) {
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException notAnOffset) {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
        }
    }
}
