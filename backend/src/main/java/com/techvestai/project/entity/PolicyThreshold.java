package com.techvestai.project.entity;

import com.techvestai.project.enums.PolicyThresholdStatus;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

@Entity
@Data
@EqualsAndHashCode(of = "id")
@ToString(exclude = "createdBy")
@Table(name = "policy_thresholds")
public class PolicyThreshold {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "approve_threshold", nullable = false)
    private int approveThreshold;

    @Column(name = "refer_threshold", nullable = false)
    private int referThreshold;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PolicyThresholdStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @PrePersist
    public void prePersist() {
        createdAt = Instant.now();
        if (status == null) {
            status = PolicyThresholdStatus.ACTIVE;
        }
    }
}
