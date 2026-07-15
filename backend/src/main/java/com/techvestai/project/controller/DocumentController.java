package com.techvestai.project.controller;

import com.techvestai.project.dto.response.DocumentMetadata;
import com.techvestai.project.entity.Document;
import com.techvestai.project.enums.DocumentType;
import com.techvestai.project.service.DocumentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.UUID;

/**
 * Document upload controller — Task 13.5.
 *
 * <p>POST /api/v1/applications/{id}/documents accepts a multipart file and
 * the document type, delegates validation and storage to {@link DocumentService},
 * and returns 201 with the persisted document metadata.
 * Size (413) and MIME type (415) errors are handled by
 * {@link com.techvestai.project.exception.GlobalExceptionHandler}.
 *
 * <p><b>Requirements:</b> 2.3–2.6
 */
@RestController
@RequestMapping("/api/v1/applications/{applicationId}/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * POST /api/v1/applications/{applicationId}/documents
     *
     * @param applicationId the owning application
     * @param file          the uploaded file (PDF, JPEG, or PNG, max 10 MB)
     * @param documentType  one of GOVERNMENT_ID, INCOME_PROOF, BANK_STATEMENT
     * @return 201 with {@link DocumentMetadata}
     */
    @PostMapping
    public ResponseEntity<DocumentMetadata> upload(
            @PathVariable UUID applicationId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") DocumentType documentType) throws IOException {

        Document saved = documentService.storeDocument(file, applicationId, documentType);

        DocumentMetadata meta = new DocumentMetadata(
                saved.getId(),
                saved.getDocumentType(),
                saved.getOriginalFilename(),
                saved.getMimeType(),
                saved.getFileSizeBytes(),
                saved.getUploadedAt(),
                saved.getValidationStatus());

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.getId())
                .toUri();

        return ResponseEntity.created(location).body(meta);
    }
}
