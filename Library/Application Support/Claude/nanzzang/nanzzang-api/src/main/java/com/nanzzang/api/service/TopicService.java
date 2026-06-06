package com.nanzzang.api.service;

import com.nanzzang.api.domain.Participation;
import com.nanzzang.api.domain.SpectatorVote;
import com.nanzzang.api.domain.Topic;
import com.nanzzang.api.domain.User;
import com.nanzzang.api.domain.repository.CommentRepository;
import com.nanzzang.api.domain.repository.ParticipationRepository;
import com.nanzzang.api.domain.repository.SpectatorVoteRepository;
import com.nanzzang.api.domain.repository.TopicRepository;
import com.nanzzang.api.domain.repository.UserRepository;
import com.nanzzang.api.dto.ParticipateRequest;
import com.nanzzang.api.dto.TopicRequest;
import com.nanzzang.api.dto.TopicResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TopicService {

    private final TopicRepository topicRepository;
    private final UserRepository userRepository;
    private final ParticipationRepository participationRepository;
    private final CommentRepository commentRepository;
    private final SpectatorVoteRepository spectatorVoteRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String VIEW_COUNT_PREFIX = "viewcount:";

    public Page<TopicResponse> getTopics(String sort, String category, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Topic> topics;

        if (category != null && !category.equals("all")) {
            topics = topicRepository.findByCategoryOrderByCreatedAtDesc(category, pageable);
        } else if ("hot".equals(sort)) {
            topics = topicRepository.findAllOrderByActiveFirstThenHotScore(LocalDateTime.now(), pageable);
        } else {
            topics = topicRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        return topics.map(this::toResponse);
    }

    public Page<TopicResponse> searchTopics(String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return topicRepository.findByTitleContainingIgnoreCaseOrderByCreatedAtDesc(keyword.trim(), pageable)
                .map(this::toResponse);
    }

    public TopicResponse getTopicDetail(UUID id) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 토픽입니다: " + id));
        return toResponse(topic);
    }

    @Transactional
    public TopicResponse createTopic(UUID userId, TopicRequest request) {
        User author = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));

        String imageUrls = (request.getImageUrls() != null && !request.getImageUrls().isEmpty())
                ? String.join(",", request.getImageUrls())
                : null;

        Topic topic = Topic.builder()
                .author(author)
                .title(request.getTitle())
                .body(request.getBody())
                .category(request.getCategory())
                .teamAName(request.getTeamAName())
                .teamBName(request.getTeamBName())
                .imageUrls(imageUrls)
                .build();

        int days = request.getDurationDays() != null ? request.getDurationDays() : 7;
        topic.setExpiresAt(LocalDateTime.now().plusDays(days));

        Topic saved = topicRepository.save(topic);
        return toResponse(saved);
    }

    public java.util.Optional<String> getMyParticipation(UUID topicId, UUID userId) {
        return participationRepository.findByTopicIdAndUserId(topicId, userId)
                .map(Participation::getTeamSide);
    }

    @Transactional
    public void deleteTopic(UUID topicId) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 토픽입니다"));
        commentRepository.findByTopicIdAndParentIsNullOrderByCreatedAtDesc(topicId)
                .forEach(c -> commentRepository.delete(c));
        participationRepository.findByTopicId(topicId)
                .forEach(p -> participationRepository.delete(p));
        topicRepository.delete(topic);
    }

    @Transactional
    public void deleteAllTopics() {
        topicRepository.findAll().forEach(topic -> {
            participationRepository.findByTopicId(topic.getId())
                    .forEach(p -> participationRepository.delete(p));
        });
        commentRepository.deleteAll();
        topicRepository.deleteAll();
    }

    public TopicResponse incrementViewCount(UUID id) {
        Topic topic = topicRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 토픽입니다: " + id));
        redisTemplate.opsForValue().increment(VIEW_COUNT_PREFIX + id);
        return toResponse(topic);
    }

    @Transactional
    public void participate(UUID topicId, UUID userId, ParticipateRequest request) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 토픽입니다"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));

        if (topic.isClosed() || topic.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("이미 종료된 대결판입니다");
        }

        // 이미 참전했는지 확인
        participationRepository.findByTopicIdAndUserId(topicId, userId)
                .ifPresent(p -> {
                    throw new IllegalStateException("이미 참전한 토픽입니다");
                });

        Participation participation = Participation.builder()
                .topic(topic)
                .user(user)
                .teamSide(request.getTeamSide())
                .build();

        participationRepository.save(participation);

        // hotScore 업데이트
        int totalVotes = participationRepository.countByTopicIdAndTeamSide(topicId, "A")
                + participationRepository.countByTopicIdAndTeamSide(topicId, "B");
        int commentCount = commentRepository.countByTopicId(topicId);
        double score = totalVotes * 1.0 + commentCount * 2.0 + topic.getViewCount() * 0.1;
        topic.updateHotScore(score);
    }

    @Transactional
    public void castSpectatorVote(UUID topicId, UUID userId, String teamSide) {
        Topic topic = topicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 토픽입니다"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));

        spectatorVoteRepository.findByTopicIdAndUserId(topicId, userId)
                .ifPresentOrElse(
                    existing -> existing.updateTeamSide(teamSide),
                    () -> spectatorVoteRepository.save(
                        SpectatorVote.builder().topic(topic).user(user).teamSide(teamSide).build()
                    )
                );
    }

    public java.util.Optional<String> getMySpectatorVote(UUID topicId, UUID userId) {
        return spectatorVoteRepository.findByTopicIdAndUserId(topicId, userId)
                .map(SpectatorVote::getTeamSide);
    }

    private TopicResponse toResponse(Topic topic) {
        int teamAVotes = participationRepository.countByTopicIdAndTeamSide(topic.getId(), "A");
        int teamBVotes = participationRepository.countByTopicIdAndTeamSide(topic.getId(), "B");
        int commentCount = commentRepository.countByTopicId(topic.getId());
        Object redisDelta = redisTemplate.opsForValue().get(VIEW_COUNT_PREFIX + topic.getId());
        int viewCount = topic.getViewCount() + (redisDelta != null ? Integer.parseInt(redisDelta.toString()) : 0);
        return TopicResponse.from(topic, teamAVotes, teamBVotes, commentCount, viewCount);
    }
}
