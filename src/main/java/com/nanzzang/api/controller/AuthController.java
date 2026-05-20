package com.nanzzang.api.controller;

import com.nanzzang.api.dto.AuthRequest;
import com.nanzzang.api.dto.AuthResponse;
import com.nanzzang.api.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 개발용 간편 로그인/회원가입
     * 이메일로 기존 회원이면 로그인, 없으면 자동 가입
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        return ResponseEntity.ok(authService.loginOrRegister(request));
    }

    // 추후 Google OAuth2 검증 및 JWT 발급 로직이 들어갈 뼈대
    @PostMapping("/google")
    public ResponseEntity<String> googleLogin(@RequestBody String idToken) {
        // TODO: idToken 검증 후 User 정보 획득/저장 및 JWT 발급
        return ResponseEntity.ok("JWT_TOKEN_PLACEHOLDER");
    }
}
