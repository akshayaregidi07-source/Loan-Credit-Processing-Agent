package com.techvestai.project.entity;

import com.techvestai.project.enums.DecisionValue;
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
@ToString(exclude = {"application", "underwriter"})
@Table(name = "underwriter_decisions")
public class UnderwriterDecision {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "underwriter_id", nullable = false)
    private User underwriter;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_value", nullable = false, length = 30)
    private DecisionValue decisionValue;

    @Column(name = "justification_text", columnDefinition = "TEXT", nullable = false)
    private String justificationText;

    @Column(name = "override_reason", columnDefinition = "TEXT")
    private String overrideReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_recommendation", nullable = false, length = 30)
    private RecommendationValue systemRecommendation;

    @Column(name = "decided_at", nullable = false)
    private Instant decidedAt;

    @PrePersist
    public void prePersist() {
        decidedAt = Instant.now();
    }
}
