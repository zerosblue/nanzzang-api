package com.nanzzang.api.controller;

import com.nanzzang.api.dto.AuthRequest;
import com.nanzzang.api.dto.AuthResponse;
import com.nanzzang.api.dto.GoogleAuthRequest;
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
}
