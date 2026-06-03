package com.nanzzang.api.dto;

import com.nanzzang.api.domain.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserResponse {
    private String email;
    private String nickname;
    private boolean isPro;
    private String role;
    private String grade;

    // 전투 통계
    private int totalParticipations;
    private int winCount;
    private int loseCount;
    private int drawCount;
    private double winRate; // 승률 (%)

    public static UserResponse of(User user, int total, int win, int lose, int draw) {
        double rate = total == 0 ? 0.0 : Math.round((double) win / total * 1000.0) / 10.0;

        return UserResponse.builder()
                .email(user.getEmail())
                .nickname(user.getNickname())
                .isPro(user.isPro())
                .role(user.getRole())
                .grade(user.getGrade().name())
                .totalParticipations(total)
                .winCount(user.getWinCount())
                .loseCount(lose)
                .drawCount(draw)
                .winRate(rate)
                .build();
    }
}
