package com.techvestai.project.dto.response;

import com.techvestai.project.enums.DocumentType;
import com.techvestai.project.enums.DocumentValidationStatus;

import java.time.Instant;
import java.util.UUID;

public record DocumentMetadata(
        UUID id,
        DocumentType documentType,
        String originalFilename,
        String mimeType,
        Long fileSizeBytes,
        Instant uploadedAt,
        DocumentValidationStatus validationStatus
) {}
