package com.techvestai.project.entity;

import com.techvestai.project.enums.FairnessOutcome;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@EqualsAndHashCode(of = "id")
@ToString(exclude = "application")
@Table(name = "fairness_results")
public class FairnessResult {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private Application application;

    @Column(name = "original_credit_score", nullable = false, precision = 7, scale = 2)
    private BigDecimal originalCreditScore;

    @Column(name = "anonymised_credit_score", nullable = false, precision = 7, scale = 2)
    private BigDecimal anonymisedCreditScore;

    @Column(name = "fairness_delta", nullable = false, precision = 7, scale = 2)
    private BigDecimal fairnessDelta;

    @Enumerated(EnumType.STRING)
    @Column(name = "fairness_outcome", nullable = false, length = 30)
    private FairnessOutcome fairnessOutcome;

    @Column(columnDefinition = "TEXT")
    private String flagReason;

    @Column(name = "evaluated_at", nullable = false)
    private Instant evaluatedAt;

    @PrePersist
    public void prePersist() {
        evaluatedAt = Instant.now();
    }
}
