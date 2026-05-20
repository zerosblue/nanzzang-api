package com.nanzzang.api.service;

import com.nanzzang.api.domain.User;
import com.nanzzang.api.domain.repository.UserRepository;
import com.nanzzang.api.dto.AuthRequest;
import com.nanzzang.api.dto.AuthResponse;
import com.nanzzang.api.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    /**
     * 개발용 간편 로그인: 이메일+닉네임으로 가입 또는 로그인
     * 이미 존재하면 로그인, 없으면 자동 가입
     */
    @Transactional
    public AuthResponse loginOrRegister(AuthRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseGet(() -> {
                    // 닉네임 중복 체크
                    userRepository.findByNickname(request.getNickname())
                            .ifPresent(u -> {
                                throw new IllegalArgumentException("이미 사용 중인 닉네임입니다: " + request.getNickname());
                            });

                    User newUser = User.builder()
                            .email(request.getEmail())
                            .nickname(request.getNickname())
                            .isPro(false)
                            .build();
                    return userRepository.save(newUser);
                });

        String token = jwtProvider.createToken(user.getId(), user.getEmail());

        return AuthResponse.builder()
                .accessToken(token)
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .role(user.getRole())
                .build();
    }
}
