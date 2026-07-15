package com.techvestai.project.repository;

import com.techvestai.project.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    List<AuditEvent> findByCreatedAtBetween(Instant from, Instant to);
}
