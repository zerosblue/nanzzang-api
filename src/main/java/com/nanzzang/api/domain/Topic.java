package com.nanzzang.api.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "topics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Topic extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User author;

    @Column(nullable = false, length = 100)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false, length = 30)
    private String category;

    @Column(name = "team_a_name", nullable = false, length = 30)
    private String teamAName;

    @Column(name = "team_b_name", nullable = false, length = 30)
    private String teamBName;

    @Column(nullable = false)
    private int viewCount = 0;

    @Column(nullable = false)
    private double hotScore = 0.0;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "is_closed", nullable = false)
    private boolean isClosed = false;

    @Column(name = "winning_team", length = 10)
    private String winningTeam; // "A", "B", "DRAW"

    @Column(name = "image_urls", columnDefinition = "TEXT")
    private String imageUrls; // comma-separated URLs

    @Builder
    public Topic(User author, String title, String body, String category, String teamAName, String teamBName, String imageUrls) {
        this.author = author;
        this.title = title;
        this.body = body;
        this.category = category;
        this.teamAName = teamAName;
        this.teamBName = teamBName;
        this.imageUrls = imageUrls;
        this.expiresAt = LocalDateTime.now().plusDays(7);
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public void closeTopic(String winningTeam) {
        this.isClosed = true;
        this.winningTeam = winningTeam;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public void updateHotScore(double score) {
        this.hotScore = score;
    }
}
