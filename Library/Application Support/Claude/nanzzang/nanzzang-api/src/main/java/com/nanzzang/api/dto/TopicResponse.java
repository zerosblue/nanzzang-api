package com.nanzzang.api.dto;

import com.nanzzang.api.domain.Topic;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
public class TopicResponse {

    private UUID id;
    private String title;
    private String body;
    private String category;
    private String teamAName;
    private String teamBName;
    private int teamAVotes;
    private int teamBVotes;
    private int commentCount;
    private int participantCount;
    private int viewCount;
    private double hotScore;
    private boolean isHot;
    private String author;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean isClosed;
    private String winningTeam;
    private List<String> images;

    public static TopicResponse from(Topic topic, int teamAVotes, int teamBVotes, int commentCount) {
        return from(topic, teamAVotes, teamBVotes, commentCount, topic.getViewCount());
    }

    public static TopicResponse from(Topic topic, int teamAVotes, int teamBVotes, int commentCount, int viewCount) {
        int total = teamAVotes + teamBVotes;
        String raw = topic.getImageUrls();
        List<String> images = (raw != null && !raw.isBlank())
                ? Arrays.asList(raw.split(","))
                : Collections.emptyList();

        return TopicResponse.builder()
                .id(topic.getId())
                .title(topic.getTitle())
                .body(topic.getBody())
                .category(topic.getCategory())
                .teamAName(topic.getTeamAName())
                .teamBName(topic.getTeamBName())
                .teamAVotes(teamAVotes)
                .teamBVotes(teamBVotes)
                .commentCount(commentCount)
                .participantCount(total)
                .viewCount(viewCount)
                .hotScore(topic.getHotScore())
                .isHot(topic.getHotScore() > 50)
                .author(topic.getAuthor().getNickname())
                .createdAt(topic.getCreatedAt())
                .expiresAt(topic.getExpiresAt())
                .isClosed(topic.isClosed())
                .winningTeam(topic.getWinningTeam())
                .images(images)
                .build();
    }
}
