package com.nanzzang.api.dto;

import com.nanzzang.api.domain.Comment;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Builder
public class CommentResponse {

    private UUID id;
    private UUID topicId;
    private UUID parentId;
    private String teamSide;
    private String content;
    private int likeCount;
    private String author;
    private LocalDateTime createdAt;
    private List<CommentResponse> children;

    public static CommentResponse from(Comment comment) {
        return CommentResponse.builder()
                .id(comment.getId())
                .topicId(comment.getTopic().getId())
                .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
                .teamSide(comment.getTeamSide())
                .content(comment.getContent())
                .likeCount(comment.getLikeCount())
                .author(comment.getUser().getNickname())
                .createdAt(comment.getCreatedAt())
                .children(comment.getChildren() != null
                        ? comment.getChildren().stream()
                            .map(CommentResponse::from)
                            .collect(Collectors.toList())
                        : List.of())
                .build();
    }
}
