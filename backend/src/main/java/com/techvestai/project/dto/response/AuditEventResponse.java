package com.techvestai.project.dto.response;

import com.techvestai.project.enums.AuditEventType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventResponse(
        UUID eventId,
        UUID applicationId,
        AuditEventType eventType,
        Map<String, Object> eventPayload,
        String actor,
        Instant createdAt
) {}
