package com.techvestai.project.entity;

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
@ToString(exclude = {"application", "policyThreshold"})
@Table(name = "credit_scores")
public class CreditScore {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private Application application;

    @Column(name = "credit_score", nullable = false, precision = 7, scale = 2)
    private BigDecimal creditScore;

    @Column(name = "dti_ratio", nullable = false, precision = 5, scale = 2)
    private BigDecimal dtiRatio;

    @Column(name = "dti_sub_score", nullable = false)
    private int dtiSubScore;

    @Column(name = "income_stability_score", nullable = false)
    private int incomeStabilityScore;

    @Column(name = "credit_history_score", nullable = false)
    private int creditHistoryScore;

    @Column(name = "dti_weight", nullable = false, precision = 4, scale = 2)
    private BigDecimal dtiWeight;

    @Column(name = "income_stability_weight", nullable = false, precision = 4, scale = 2)
    private BigDecimal incomeStabilityWeight;

    @Column(name = "credit_history_weight", nullable = false, precision = 4, scale = 2)
    private BigDecimal creditHistoryWeight;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_threshold_id", nullable = false)
    private PolicyThreshold policyThreshold;

    @Column(name = "computed_at", nullable = false)
    private Instant computedAt;

    @PrePersist
    public void prePersist() {
        computedAt = Instant.now();
    }
}
