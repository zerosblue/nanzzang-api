package com.nanzzang.api.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "reports")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Column(name = "target_type", nullable = false, length = 20)
    private String targetType; // "TOPIC" or "COMMENT"

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false)
    private boolean isResolved = false;

    @Builder
    public Report(User reporter, String targetType, UUID targetId, String reason) {
        this.reporter = reporter;
        this.targetType = targetType;
        this.targetId = targetId;
        this.reason = reason;
    }

    public void resolveReport() {
        this.isResolved = true;
    }
}
