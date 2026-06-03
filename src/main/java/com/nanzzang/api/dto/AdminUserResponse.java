package com.nanzzang.api.dto;

import com.nanzzang.api.domain.User;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AdminUserResponse {
    private String nickname;
    private String email;
    private String role;
    private String grade;
    private LocalDateTime createdAt;

    public static AdminUserResponse from(User user) {
        return AdminUserResponse.builder()
                .nickname(user.getNickname())
                .email(user.getEmail())
                .role(user.getRole())
                .grade(user.getGrade().name())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
