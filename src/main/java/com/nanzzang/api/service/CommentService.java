package com.nanzzang.api.service;

import com.nanzzang.api.domain.Comment;
import com.nanzzang.api.domain.Topic;
import com.nanzzang.api.domain.User;
import com.nanzzang.api.domain.repository.CommentRepository;
import com.nanzzang.api.domain.repository.TopicRepository;
import com.nanzzang.api.domain.repository.UserRepository;
import com.nanzzang.api.dto.CommentRequest;
import com.nanzzang.api.dto.CommentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationService notificationService;

    public List<CommentResponse> getCommentsByTopic(UUID topicId) {
        List<Comment> comments = commentRepository.findByTopicIdAndParentIsNullOrderByCreatedAtDesc(topicId);
        return comments.stream()
                .map(CommentResponse::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public CommentResponse createComment(UUID topicId, UUID userId, CommentRequest request) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 토픽입니다"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));

        Comment parent = null;
        if (request.getParentId() != null) {
            parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 부모 댓글입니다"));
        }

        Comment comment = Comment.builder()
                .topic(topic)
                .user(user)
                .parent(parent)
                .teamSide(request.getTeamSide())
                .content(request.getContent())
                .build();

        Comment saved = commentRepository.save(comment);
        CommentResponse response = CommentResponse.from(saved);

        // WebSocket 실시간 브로드캐스트
        messagingTemplate.convertAndSend("/topic/comments/" + topicId, response);

        // 토픽 작성자에게 댓글 알림 (자신의 댓글 제외)
        UUID authorId = topic.getAuthor().getId();
        if (!authorId.equals(userId)) {
            notificationService.send(authorId, "new_comment", Map.of(
                "topicId", topicId.toString(),
                "topicTitle", topic.getTitle(),
                "commenter", user.getNickname(),
                "preview", comment.getContent().length() > 30
                    ? comment.getContent().substring(0, 30) + "..."
                    : comment.getContent()
            ));
        }

        return response;
    }

    @Transactional
    public CommentResponse toggleLike(UUID commentId) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 댓글입니다"));

        // 간단한 토글 - 실제로는 사용자별 좋아요 추적이 필요
        comment.incrementLikeCount();
        return CommentResponse.from(comment);
    }
}
