package com.nanzzang.api.service;

import com.nanzzang.api.domain.PasswordResetToken;
import com.nanzzang.api.domain.User;
import com.nanzzang.api.domain.repository.PasswordResetTokenRepository;
import com.nanzzang.api.domain.repository.UserRepository;
import com.nanzzang.api.dto.*;
import com.nanzzang.api.security.JwtProvider;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final JwtProvider jwtProvider;
    private final TelegramNotificationService telegramNotificationService;
    private final PasswordEncoder passwordEncoder;

    @Setter(onMethod_ = @Autowired(required = false))
    private JavaMailSender mailSender;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${app.frontend-url:https://nanzzang.vercel.app}")
    private String frontendUrl;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    /** 이메일+비밀번호+닉네임 회원가입 */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        userRepository.findByNickname(request.getNickname())
                .ifPresent(u -> { throw new IllegalArgumentException("이미 사용 중인 닉네임입니다."); });

        User user = userRepository.save(User.builder()
                .email(request.getEmail())
                .nickname(request.getNickname())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .provider("email")
                .isPro(false)
                .build());

        telegramNotificationService.send(
                "🆕 <b>신규 가입 (이메일)</b>\n닉네임: " + user.getNickname() + "\n이메일: " + user.getEmail()
        );

        String token = jwtProvider.createToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        return buildResponse(user, token, refreshToken);
    }

    /** 이메일+비밀번호 로그인 */
    @Transactional(readOnly = true)
    public AuthResponse emailLogin(EmailLoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다."));

        if (user.getPasswordHash() == null) {
            throw new IllegalArgumentException("이 계정은 구글 로그인으로 가입되었습니다. 구글 로그인을 이용해주세요.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        String token = jwtProvider.createToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        return buildResponse(user, token, refreshToken);
    }

    /** 비밀번호 재설정 이메일 발송 */
    @Transactional
    public void requestPasswordReset(ForgotPasswordRequest request) {
        Optional<User> userOpt = userRepository.findByEmail(request.getEmail());
        if (userOpt.isEmpty()) return; // 이메일 존재 여부 노출 방지 (응답은 항상 200)

        User user = userOpt.get();
        if (user.getPasswordHash() == null) return; // 구글 전용 계정은 무시

        if (mailSender == null) {
            log.warn("메일 서비스 미설정 (MAIL_USERNAME/MAIL_PASSWORD 환경변수 확인 필요)");
            throw new IllegalStateException("메일 서비스가 설정되지 않았습니다. 관리자에게 문의해주세요.");
        }

        String token = UUID.randomUUID().toString();
        passwordResetTokenRepository.save(PasswordResetToken.builder()
                .token(token)
                .email(user.getEmail())
                .expiresAt(LocalDateTime.now().plusMinutes(30))
                .build());

        String resetLink = frontendUrl + "/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(user.getEmail());
        message.setSubject("[NANZZANG] 비밀번호 재설정 안내");
        message.setText(
                "안녕하세요, " + user.getNickname() + "님!\n\n" +
                "아래 링크를 클릭하면 비밀번호를 재설정할 수 있습니다.\n" +
                "링크는 30분간 유효합니다.\n\n" +
                resetLink + "\n\n" +
                "본인이 요청하지 않으셨다면 이 이메일을 무시하세요.\n\n" +
                "— NANZZANG 팀"
        );
        mailSender.send(message);
    }

    /** 비밀번호 재설정 처리 */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(request.getToken())
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 재설정 링크입니다."));

        if (resetToken.isUsed()) throw new IllegalArgumentException("이미 사용된 재설정 링크입니다.");
        if (resetToken.isExpired()) throw new IllegalArgumentException("만료된 재설정 링크입니다. 다시 요청해주세요.");

        User user = userRepository.findByEmail(resetToken.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다."));

        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
        resetToken.markUsed();
    }

    /** 봇 전용 간편 로그인 (이메일+닉네임) */
    @Transactional
    public AuthResponse loginOrRegister(AuthRequest request) {
        boolean[] isNew = {false};
        User user = userRepository.findByEmail(request.getEmail())
                .orElseGet(() -> {
                    userRepository.findByNickname(request.getNickname())
                            .ifPresent(u -> {
                                throw new IllegalArgumentException("이미 사용 중인 닉네임입니다: " + request.getNickname());
                            });
                    isNew[0] = true;
                    return userRepository.save(User.builder()
                            .email(request.getEmail())
                            .nickname(request.getNickname())
                            .isPro(false)
                            .build());
                });

        if (isNew[0]) {
            telegramNotificationService.send(
                "🆕 <b>신규 가입</b>\n닉네임: " + user.getNickname() + "\n이메일: " + user.getEmail()
            );
        }

        String token = jwtProvider.createToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        return buildResponse(user, token, refreshToken);
    }

    /** Google ID Token으로 로그인 또는 자동 회원가입 */
    @Transactional
    public AuthResponse googleLogin(String idToken) {
        Map<String, Object> info = verifyGoogleToken(idToken);

        String email = (String) info.get("email");
        String name  = (String) info.getOrDefault("name", "");

        boolean[] isNew = {false};
        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    String nickname = generateUniqueNickname(name.isBlank() ? email.split("@")[0] : name);
                    isNew[0] = true;
                    return userRepository.save(User.builder()
                            .email(email)
                            .nickname(nickname)
                            .provider("google")
                            .isPro(false)
                            .build());
                });

        if (isNew[0]) {
            telegramNotificationService.send(
                "🆕 <b>신규 가입 (Google)</b>\n닉네임: " + user.getNickname() + "\n이메일: " + user.getEmail()
            );
        }

        String token = jwtProvider.createToken(user.getId(), user.getEmail());
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        return buildResponse(user, token, refreshToken);
    }

    /** Refresh Token으로 새 Access Token 발급 */
    public AuthResponse refresh(String refreshToken) {
        if (!jwtProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("유효하지 않은 리프레시 토큰입니다.");
        }
        UUID userId = jwtProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));
        String newAccessToken = jwtProvider.createToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtProvider.createRefreshToken(user.getId());
        return buildResponse(user, newAccessToken, newRefreshToken);
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

    private AuthResponse buildResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .userId(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .role(user.getRole())
                .grade(user.getGrade().name())
                .build();
    }
}
