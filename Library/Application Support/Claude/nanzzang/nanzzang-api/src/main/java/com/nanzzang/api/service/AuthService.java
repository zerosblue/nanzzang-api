package com.nanzzang.api.service;

import com.nanzzang.api.domain.User;
import com.nanzzang.api.domain.repository.UserRepository;
import com.nanzzang.api.dto.AuthRequest;
import com.nanzzang.api.dto.AuthResponse;
import com.nanzzang.api.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    /** 봇 전용 간편 로그인 (이메일+닉네임) */
    @Transactional
    public AuthResponse loginOrRegister(AuthRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseGet(() -> {
                    userRepository.findByNickname(request.getNickname())
                            .ifPresent(u -> {
                                throw new IllegalArgumentException("이미 사용 중인 닉네임입니다: " + request.getNickname());
                            });
                    return userRepository.save(User.builder()
                            .email(request.getEmail())
                            .nickname(request.getNickname())
                            .isPro(false)
                            .build());
                });

        String token = jwtProvider.createToken(user.getId(), user.getEmail());
        return buildResponse(user, token);
    }

    /** Google ID Token으로 로그인 또는 자동 회원가입 */
    @Transactional
    public AuthResponse googleLogin(String idToken) {
        Map<String, Object> info = verifyGoogleToken(idToken);

        String email = (String) info.get("email");
        String name  = (String) info.getOrDefault("name", "");

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    String nickname = generateUniqueNickname(name.isBlank() ? email.split("@")[0] : name);
                    return userRepository.save(User.builder()
                            .email(email)
                            .nickname(nickname)
                            .isPro(false)
                            .build());
                });

        String token = jwtProvider.createToken(user.getId(), user.getEmail());
        return buildResponse(user, token);
    }

    private Map<String, Object> verifyGoogleToken(String idToken) {
        RestTemplate restTemplate = new RestTemplate();
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(
                    "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken,
                    Map.class
            );
            if (response == null) throw new IllegalArgumentException("Google 인증 응답이 없습니다.");
            String aud = (String) response.get("aud");
            if (!googleClientId.equals(aud)) throw new IllegalArgumentException("유효하지 않은 토큰입니다.");
            return response;
        } catch (HttpClientErrorException e) {
            throw new IllegalArgumentException("Google 인증에 실패했습니다.");
        }
    }

    private String generateUniqueNickname(String baseName) {
        String base = baseName.replaceAll("[^가-힣a-zA-Z0-9]", "");
        if (base.isBlank()) base = "유저";
        if (base.length() > 8) base = base.substring(0, 8);

        String nickname = base;
        Random random = new Random();
        int attempts = 0;
        while (userRepository.findByNickname(nickname).isPresent()) {
            nickname = base + (1000 + random.nextInt(9000));
            if (++attempts > 10) throw new IllegalStateException("닉네임 생성에 실패했습니다.");
        }
        return nickname;
    }

    private AuthResponse buildResponse(User user, String token) {
        return AuthResponse.builder()
                .accessToken(token)
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .role(user.getRole())
                .build();
    }
}
