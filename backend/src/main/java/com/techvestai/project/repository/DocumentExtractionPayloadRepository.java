package com.techvestai.project.repository;

import com.techvestai.project.entity.DocumentExtractionPayload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentExtractionPayloadRepository extends JpaRepository<DocumentExtractionPayload, UUID> {

    Optional<DocumentExtractionPayload> findByApplication_Id(UUID applicationId);
}
