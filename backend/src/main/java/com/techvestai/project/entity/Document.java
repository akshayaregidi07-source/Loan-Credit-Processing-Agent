package com.techvestai.project.entity;

import com.techvestai.project.enums.DocumentType;
import com.techvestai.project.enums.DocumentValidationStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"application"})
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 50)
    private DocumentType documentType;

    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "storage_path", nullable = false, length = 512)
    private String storagePath;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", length = 50)
    private DocumentValidationStatus validationStatus;

    @Column(name = "validation_failure_reason", columnDefinition = "TEXT")
    private String validationFailureReason;

    @PrePersist
    public void prePersist() {
        uploadedAt = Instant.now();
        if (validationStatus == null) {
            validationStatus = DocumentValidationStatus.PENDING;
        }
    }
}
