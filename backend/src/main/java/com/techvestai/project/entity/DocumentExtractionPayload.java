package com.techvestai.project.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Data
@EqualsAndHashCode(of = "id")
@ToString(exclude = "application")
@Table(name = "document_extraction_payloads")
public class DocumentExtractionPayload {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private Application application;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extracted_fields", columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> extractedFields;

    @Column(name = "extraction_status", nullable = false, length = 50)
    private String extractionStatus;

    @Column(name = "extracted_at", nullable = false)
    private Instant extractedAt;

    @PrePersist
    public void prePersist() {
        extractedAt = Instant.now();
    }
}
