package com.techvestai.project.controller;

import com.techvestai.project.dto.response.AuditEventResponse;
import com.techvestai.project.repository.AuditEventRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Audit log controller — Task 13.10.
 *
 * <p>GET /api/v1/audit/{applicationId} — UNDERWRITER + ADMIN: ordered audit trail.<br>
 * GET /api/v1/audit/export  — ADMIN only: export events by date range.
 *
 * <p><b>Requirements:</b> 8.3, 8.4
 */
@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditEventRepository auditEventRepository;

    public AuditController(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * GET /api/v1/audit/{applicationId}
     * Returns all audit events for the application ordered by created_at ASC.
     */
    @GetMapping("/{applicationId}")
    public ResponseEntity<List<AuditEventResponse>> getTrail(
            @PathVariable UUID applicationId) {

        List<AuditEventResponse> events = auditEventRepository
                .findByApplicationIdOrderByCreatedAtAsc(applicationId)
                .stream()
                .map(e -> new AuditEventResponse(
                        e.getEventId(),
                        e.getApplicationId(),
                        e.getEventType(),
                        e.getEventPayload(),
                        e.getActor(),
                        e.getCreatedAt()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(events);
    }

    /**
     * GET /api/v1/audit/export?from=YYYY-MM-DD&to=YYYY-MM-DD
     * Exports all audit events within the date range as a JSON array.
     * Both dates are inclusive; time range is midnight-to-midnight UTC.
     */
    @GetMapping("/export")
    public ResponseEntity<List<AuditEventResponse>> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        Instant fromInstant = from.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant toInstant   = to.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        List<AuditEventResponse> events = auditEventRepository
                .findByCreatedAtBetween(fromInstant, toInstant)
                .stream()
                .map(e -> new AuditEventResponse(
                        e.getEventId(),
                        e.getApplicationId(),
                        e.getEventType(),
                        e.getEventPayload(),
                        e.getActor(),
                        e.getCreatedAt()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(events);
    }
}
