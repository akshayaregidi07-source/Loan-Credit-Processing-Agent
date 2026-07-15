package com.techvestai.project.entity;

import com.techvestai.project.enums.RecommendationValue;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@EqualsAndHashCode(of = "id")
@ToString(exclude = {"application", "policyThreshold"})
@Table(name = "recommendations")
public class Recommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_value", nullable = false, length = 30)
    private RecommendationValue recommendationValue;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_threshold_id", nullable = false)
    private PolicyThreshold policyThreshold;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String explanation;

    @Column(name = "produced_at", nullable = false)
    private Instant producedAt;

    @PrePersist
    public void prePersist() {
        producedAt = Instant.now();
    }
}
