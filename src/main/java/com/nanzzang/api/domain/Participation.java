package com.nanzzang.api.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "participations", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"topic_id", "user_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Participation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "team_side", nullable = false, length = 1)
    private String teamSide; // 'A' or 'B'

    @Builder
    public Participation(Topic topic, User user, String teamSide) {
        this.topic = topic;
        this.user = user;
        this.teamSide = teamSide;
    }
}
