package com.techvestai.project.repository;

import com.techvestai.project.entity.Document;
import com.techvestai.project.enums.DocumentType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findByApplication_Id(UUID applicationId);

    Optional<Document> findByApplication_IdAndDocumentType(UUID applicationId, DocumentType documentType);

    List<Document> findAllByApplication_IdAndDocumentTypeIn(UUID applicationId, List<DocumentType> documentTypes);
}
