package com.nanzzang.api.service;

import com.nanzzang.api.domain.Participation;
import com.nanzzang.api.domain.Topic;
import com.nanzzang.api.domain.User;
import com.nanzzang.api.domain.repository.ParticipationRepository;
import com.nanzzang.api.domain.repository.UserRepository;
import com.nanzzang.api.dto.AdminUserResponse;
import com.nanzzang.api.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final ParticipationRepository participationRepository;

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

    public Page<AdminUserResponse> getAdminUsers(int page, int size) {
        return userRepository.findByRoleNotOrderByCreatedAtDesc("BOT", PageRequest.of(page, size))
                .map(AdminUserResponse::from);
    }

    public long countNonBotUsers() {
        return userRepository.countByRoleNot("BOT");
    }
}
