package com.nanzzang.api.controller;

import com.nanzzang.api.dto.*;
import com.nanzzang.api.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /** 이메일+비밀번호+닉네임 회원가입 */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    /** 이메일+비밀번호 로그인 */
    @PostMapping("/email-login")
    public ResponseEntity<AuthResponse> emailLogin(@Valid @RequestBody EmailLoginRequest request) {
        return ResponseEntity.ok(authService.emailLogin(request));
    }

    /** 비밀번호 재설정 이메일 발송 */
    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.requestPasswordReset(request);
        return ResponseEntity.ok(Map.of("message", "비밀번호 재설정 이메일을 발송했습니다."));
    }

    /** 비밀번호 재설정 처리 */
    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok(Map.of("message", "비밀번호가 재설정되었습니다."));
    }

    /** 봇 전용 간편 로그인 */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.loginOrRegister(request));
    }

    /** Google ID Token 로그인 / 자동 회원가입 */
    @PostMapping("/google")
    public ResponseEntity<AuthResponse> googleLogin(@RequestBody GoogleAuthRequest request) {
        return ResponseEntity.ok(authService.googleLogin(request.getIdToken()));
    }

    /** Refresh Token으로 새 Access Token 발급 */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }
}
