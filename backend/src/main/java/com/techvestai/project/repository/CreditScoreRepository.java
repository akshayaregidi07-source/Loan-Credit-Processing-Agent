package com.techvestai.project.repository;

import com.techvestai.project.entity.CreditScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CreditScoreRepository extends JpaRepository<CreditScore, UUID> {

    Optional<CreditScore> findByApplication_Id(UUID applicationId);
}
