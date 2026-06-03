package com.nanzzang.api.service;

import com.nanzzang.api.domain.Participation;
import com.nanzzang.api.domain.Topic;
import com.nanzzang.api.domain.User;
import com.nanzzang.api.domain.repository.CommentRepository;
import com.nanzzang.api.domain.repository.ParticipationRepository;
import com.nanzzang.api.domain.repository.TopicRepository;
import com.nanzzang.api.domain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicScheduler {

    private final TopicRepository topicRepository;
    private final ParticipationRepository participationRepository;
    private final CommentRepository commentRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String VIEW_COUNT_PREFIX = "viewcount:";

    // 매 1분마다 실행 (실제 상용에서는 10분이나 1시간 단위로 조절 가능)
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void closeExpiredTopics() {
        log.info("⏰ 만료된 토픽 검사 시작...");
        LocalDateTime now = LocalDateTime.now();
        
        // 종료 시간이 지났지만 아직 닫히지 않은 토픽들 조회
        List<Topic> expiredTopics = topicRepository.findByExpiresAtBeforeAndIsClosedFalse(now);

        for (Topic topic : expiredTopics) {
            log.info("만료된 토픽 처리 중: {}", topic.getTitle());
            
            int teamAVotes = participationRepository.countByTopicIdAndTeamSide(topic.getId(), "A");
            int teamBVotes = participationRepository.countByTopicIdAndTeamSide(topic.getId(), "B");
            
            // 댓글 수 (부모 댓글 기준)
            int commentCountA = 0; // A팀 댓글 수 (간단히 투표수로 대체 가능하나, 확장성을 위해 분리)
            int commentCountB = 0;
            // TODO: 추후 특정 진영의 댓글 수/좋아요 수를 추가하여 복합점수를 더 정교하게 만들 수 있음.
            
            // 승패 판정 로직 (득표수 중심)
            String winningTeam;
            if (teamAVotes > teamBVotes) {
                winningTeam = "A";
            } else if (teamBVotes > teamAVotes) {
                winningTeam = "B";
            } else {
                winningTeam = "DRAW"; // 무승부
            }

            topic.closeTopic(winningTeam);
            log.info("토픽 종료 완료 [{}] 승리 팀: {}", topic.getTitle(), winningTeam);

            // 참전자들에게 결과 알림 + 승자 winCount 증가
            String finalWinner = winningTeam;
            List<Participation> participants = participationRepository.findByTopicId(topic.getId());
            String winTeamName = "A".equals(winningTeam) ? topic.getTeamAName()
                    : "B".equals(winningTeam) ? topic.getTeamBName() : "무승부";
            for (Participation p : participants) {
                boolean isWinner = p.getTeamSide().equals(finalWinner);
                if (isWinner) {
                    User user = p.getUser();
                    user.incrementWinCount();
                    userRepository.save(user);
                }
                notificationService.send(p.getUser().getId(), "topic_closed", Map.of(
                    "topicId", topic.getId().toString(),
                    "topicTitle", topic.getTitle(),
                    "winningTeam", winTeamName,
                    "result", "DRAW".equals(finalWinner) ? "무승부" : (isWinner ? "승리" : "패배")
                ));
            }
        }
        
        if (!expiredTopics.isEmpty()) {
            topicRepository.saveAll(expiredTopics);
            log.info("총 {}개의 토픽이 종료 처리되었습니다.", expiredTopics.size());
        }
    }

    // 매 5분마다 Redis에 누적된 viewCount를 DB에 반영
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void flushViewCounts() {
        Set<String> keys = redisTemplate.keys(VIEW_COUNT_PREFIX + "*");
        if (keys == null || keys.isEmpty()) return;

        for (String key : keys) {
            try {
                Object raw = redisTemplate.opsForValue().get(key);
                if (raw == null) continue;
                long delta = Long.parseLong(raw.toString());
                if (delta <= 0) continue;

                // 읽은 만큼만 차감 (새로 들어오는 increment 보존)
                redisTemplate.opsForValue().increment(key, -delta);

                UUID topicId = UUID.fromString(key.substring(VIEW_COUNT_PREFIX.length()));
                topicRepository.incrementViewCount(topicId, delta);
            } catch (Exception e) {
                log.warn("viewCount flush 실패 — key: {}, error: {}", key, e.getMessage());
            }
        }
    }
}
