package com.techvestai.project.repository;

import com.techvestai.project.entity.PolicyThreshold;
import com.techvestai.project.enums.PolicyThresholdStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PolicyThresholdRepository extends JpaRepository<PolicyThreshold, UUID> {

    Optional<PolicyThreshold> findByStatus(PolicyThresholdStatus status);

    List<PolicyThreshold> findAllByOrderByCreatedAtDesc();
}
