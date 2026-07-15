package com.techvestai.project.service;

import com.techvestai.project.entity.Application;
import com.techvestai.project.entity.Document;
import com.techvestai.project.enums.DocumentType;
import com.techvestai.project.exception.DocumentValidationException;
import com.techvestai.project.repository.ApplicationRepository;
import com.techvestai.project.repository.DocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

/**
 * Document storage service — Task 12.2.
 *
 * <p>Validates each uploaded file (size, MIME type, magic bytes) before
 * streaming it to the configured file-system location. Storage paths use a
 * UUID prefix so they are non-predictable and non-enumerable by applicant ID
 * (Requirement 13.2).
 *
 * <p><b>Requirements:</b> 2.3–2.6, 13.2, 13.3
 */
@Service
@Transactional
public class DocumentService {

    private static final long MAX_FILE_BYTES = 10L * 1024 * 1024; // 10 MB

    private static final Set<String> ACCEPTED_MIME_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png");

    // Magic-byte signatures
    private static final byte[] PDF_MAGIC  = {0x25, 0x50, 0x44, 0x46};         // %PDF
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC  = {(byte) 0x89, 0x50, 0x4E, 0x47}; // ‌PNG

    @Value("${app.document.storage-base-dir:./uploads}")
    private String storageBaseDir;

    private final ApplicationRepository applicationRepository;
    private final DocumentRepository    documentRepository;

    public DocumentService(ApplicationRepository applicationRepository,
                           DocumentRepository documentRepository) {
        this.applicationRepository = applicationRepository;
        this.documentRepository    = documentRepository;
    }

    /**
     * Validates and stores an uploaded document, then persists its metadata.
     *
     * @param file          the multipart upload
     * @param applicationId the owning application
     * @param documentType  the declared document type
     * @return the persisted {@link Document} entity
     * @throws DocumentValidationException if size, MIME type, or magic bytes are invalid
     * @throws IOException                 if the file cannot be written to storage
     */
    public Document storeDocument(MultipartFile file,
                                  UUID applicationId,
                                  DocumentType documentType) throws IOException {
        // ---- Size validation ----
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new DocumentValidationException(
                    "File exceeds the maximum allowed size of 10 MB");
        }

        // ---- MIME type validation (Content-Type header) ----
        String contentType = file.getContentType();
        if (contentType == null || !ACCEPTED_MIME_TYPES.contains(contentType.toLowerCase())) {
            throw new DocumentValidationException(
                    "Unsupported file type '" + contentType
                            + "'. Accepted: application/pdf, image/jpeg, image/png");
        }

        // ---- Magic-byte validation (Requirement 13.3 — no MIME spoofing) ----
        byte[] header = readHeader(file, 4);
        validateMagicBytes(contentType, header);

        // ---- Store file to non-predictable UUID-based path ----
        Application application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new DocumentValidationException(
                        "Application not found: " + applicationId));

        String sanitisedFilename = sanitise(file.getOriginalFilename());
        String relativePath = UUID.randomUUID() + "/" + sanitisedFilename;
        Path target = Paths.get(storageBaseDir).resolve(relativePath);
        Files.createDirectories(target.getParent());

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        // ---- Persist Document metadata ----
        Document document = new Document();
        document.setApplication(application);
        document.setDocumentType(documentType);
        document.setOriginalFilename(sanitisedFilename);
        document.setMimeType(contentType);
        document.setFileSizeBytes(file.getSize());
        document.setStoragePath(relativePath);

        return documentRepository.save(document);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private byte[] readHeader(MultipartFile file, int bytes) throws IOException {
        try (InputStream is = file.getInputStream()) {
            return is.readNBytes(bytes);
        }
    }

    private void validateMagicBytes(String mimeType, byte[] header) {
        boolean valid = switch (mimeType.toLowerCase()) {
            case "application/pdf" -> startsWith(header, PDF_MAGIC);
            case "image/jpeg"      -> startsWith(header, JPEG_MAGIC);
            case "image/png"       -> startsWith(header, PNG_MAGIC);
            default -> false;
        };
        if (!valid) {
            throw new DocumentValidationException(
                    "File content does not match the declared MIME type '" + mimeType + "'");
        }
    }

    private boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) return false;
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) return false;
        }
        return true;
    }

    private String sanitise(String filename) {
        if (filename == null || filename.isBlank()) {
            return "upload";
        }
        // Keep only safe characters: letters, digits, dot, underscore, hyphen
        return filename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
