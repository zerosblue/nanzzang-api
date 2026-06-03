package com.nanzzang.api.service;

import com.nanzzang.api.domain.Participation;
import com.nanzzang.api.domain.Topic;
import com.nanzzang.api.domain.User;
import com.nanzzang.api.domain.repository.ParticipationRepository;
import com.nanzzang.api.domain.repository.TopicRepository;
import com.nanzzang.api.domain.repository.UserRepository;
import com.nanzzang.api.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final ParticipationRepository participationRepository;
    private final TopicRepository topicRepository;

    public Map<String, Object> getAdminStats() {
        long totalUsers = userRepository.count();
        long totalVisitors = topicRepository.findAll().stream()
                .mapToLong(Topic::getViewCount)
                .sum();
        double conversionRate = totalVisitors > 0
                ? Math.round((double) totalUsers / totalVisitors * 1000.0) / 10.0
                : 0.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("todayVisitors", 0L);
        stats.put("totalVisitors", totalVisitors);
        stats.put("totalUsers", totalUsers);
        stats.put("conversionRate", conversionRate);
        return stats;
    }

    public Map<String, Object> getAdminUsers(int page, int size) {
        Page<User> userPage = userRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        Page<Map<String, Object>> userRows = userPage.map(user -> {
            List<Participation> parts = participationRepository.findByUserId(user.getId());
            int winCount = (int) parts.stream()
                    .filter(p -> p.getTopic().isClosed()
                            && p.getTeamSide().equals(p.getTopic().getWinningTeam()))
                    .count();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("email", user.getEmail());
            row.put("nickname", user.getNickname());
            row.put("role", user.getRole());
            row.put("grade", calculateGrade(winCount));
            row.put("createdAt", user.getCreatedAt());
            return row;
        });

        Map<String, Object> result = new HashMap<>();
        result.put("users", userRows);
        result.put("totalUsers", userPage.getTotalElements());
        return result;
    }

    private String calculateGrade(int winCount) {
        if (winCount >= 100) return "LEGEND";
        if (winCount >= 50) return "DIAMOND";
        if (winCount >= 30) return "PLATINUM";
        if (winCount >= 15) return "GOLD";
        if (winCount >= 5) return "SILVER";
        return "BRONZE";
    }

    public UserResponse getUserStats(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));

        List<Participation> participations = participationRepository.findByUserId(userId);
        
        int total = participations.size();
        int win = 0;
        int lose = 0;
        int draw = 0;

        for (Participation p : participations) {
            Topic topic = p.getTopic();
            if (!topic.isClosed()) {
                continue; // 아직 진행 중인 토픽은 승패 계산 보류
            }
            
            if ("DRAW".equals(topic.getWinningTeam())) {
                draw++;
            } else if (p.getTeamSide().equals(topic.getWinningTeam())) {
                win++;
            } else {
                lose++;
            }
        }

        return UserResponse.of(user, total, win, lose, draw);
    }
}
