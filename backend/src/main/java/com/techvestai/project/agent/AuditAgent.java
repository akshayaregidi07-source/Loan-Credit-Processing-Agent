package com.techvestai.project.agent;

import com.techvestai.project.entity.AuditEvent;
import com.techvestai.project.enums.AuditEventType;
import com.techvestai.project.repository.AuditEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Audit Agent — append-only event persistence.
 *
 * <p>Records every agent action and Underwriter decision as an immutable
 * {@link AuditEvent} row. The method runs in its own transaction so that
 * an audit record is written even when the calling transaction is about
 * to roll back (e.g. after a pipeline halt).
 *
 * <p>No update or delete paths are exposed. The underlying PostgreSQL rules
 * in V4__create_audit_events.sql silently discard any UPDATE or DELETE on
 * the {@code audit_events} table.
 *
 * <p><b>Requirements:</b> 8.1, 8.2, 8.5, 8.6
 */
@Component
public class AuditAgent {

    private static final Logger log = LoggerFactory.getLogger(AuditAgent.class);

    private final AuditEventRepository auditEventRepository;

    public AuditAgent(AuditEventRepository auditEventRepository) {
        this.auditEventRepository = auditEventRepository;
    }

    /**
     * Persists a single audit event.
     *
     * <p>Runs in {@link Propagation#REQUIRES_NEW} so the write is committed
     * independently of the caller's transaction — ensuring the event is
     * stored even when the calling unit of work fails or rolls back.
     *
     * @param eventType     the type of event being recorded
     * @param applicationId the application this event belongs to
     * @param actor         the agent name or authenticated user identifier
     * @param payload       arbitrary key-value data serialised as JSONB
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordEvent(AuditEventType eventType,
                            UUID applicationId,
                            String actor,
                            Map<String, Object> payload) {
        AuditEvent event = new AuditEvent();
        event.setEventType(eventType);
        event.setApplicationId(applicationId);
        event.setActor(actor);
        event.setEventPayload(payload);
        // createdAt is set by the @PrePersist hook on AuditEvent

        AuditEvent saved = auditEventRepository.save(event);
        log.debug("Audit event recorded: type={} appId={} eventId={}",
                eventType, applicationId, saved.getEventId());
    }
}
