package com.nanzzang.api.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class AuthResponse {

    private String accessToken;
    private UUID userId;
    private String email;
    private String nickname;
    private String role;
}
