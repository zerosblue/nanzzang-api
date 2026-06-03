package com.nanzzang.api.controller;

import com.nanzzang.api.domain.User;
import com.nanzzang.api.domain.repository.UserRepository;
import com.nanzzang.api.dto.UserResponse;
import com.nanzzang.api.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;

    private void validateAdmin(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));
        if (!"ADMIN".equals(user.getRole())) {
            throw new SecurityException("관리자 권한이 필요합니다");
        }
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyStats(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(userService.getUserStats(userId));
    }

    @GetMapping("/admin/stats")
    public ResponseEntity<Map<String, Object>> getAdminStats(Authentication authentication) {
        validateAdmin(authentication);
        return ResponseEntity.ok(userService.getAdminStats());
    }

    @GetMapping("/admin/list")
    public ResponseEntity<Map<String, Object>> getAdminUsers(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validateAdmin(authentication);
        return ResponseEntity.ok(userService.getAdminUsers(page, size));
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<String> handleSecurityException(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
}
