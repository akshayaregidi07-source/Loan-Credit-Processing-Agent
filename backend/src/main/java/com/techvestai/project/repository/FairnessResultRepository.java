package com.techvestai.project.repository;

import com.techvestai.project.entity.FairnessResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FairnessResultRepository extends JpaRepository<FairnessResult, UUID> {

    Optional<FairnessResult> findByApplication_Id(UUID applicationId);
}
