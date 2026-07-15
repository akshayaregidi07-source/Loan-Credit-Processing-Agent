package com.techvestai.project.repository;

import com.techvestai.project.entity.Application;
import com.techvestai.project.enums.ApplicationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    Optional<Application> findByIdAndApplicantId(UUID id, Long applicantId);

    List<Application> findByStatus(ApplicationStatus status);

    Page<Application> findAllByStatus(ApplicationStatus status, Pageable pageable);
}
