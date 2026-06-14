package com.nanzzang.api.dto;

import com.nanzzang.api.domain.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class AdminUserResponse {
    private UUID id;
    private String nickname;
    private String email;
    private String role;
    private String grade;
    private LocalDateTime createdAt;
    private LocalDateTime lastVisitedAt;

    public static AdminUserResponse from(User user) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .role(user.getRole())
                .grade(user.getGrade().name())
                .createdAt(user.getCreatedAt())
                .lastVisitedAt(user.getLastVisitedAt())
                .build();
    }
}
