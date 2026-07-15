package com.techvestai.project.repository;

import com.techvestai.project.entity.UnderwriterDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnderwriterDecisionRepository extends JpaRepository<UnderwriterDecision, UUID> {

    Optional<UnderwriterDecision> findByApplication_Id(UUID applicationId);
}
