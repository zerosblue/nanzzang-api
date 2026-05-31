package com.nanzzang.api.controller;

import com.nanzzang.api.domain.User;
import com.nanzzang.api.domain.repository.UserRepository;
import com.nanzzang.api.domain.repository.VisitorLogRepository;
import com.nanzzang.api.dto.AdminUserResponse;
import com.nanzzang.api.dto.UserResponse;
import com.nanzzang.api.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final VisitorLogRepository visitorLogRepository;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyStats(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(userService.getUserStats(userId));
    }

    @GetMapping("/admin/list")
    public ResponseEntity<?> getAdminUserList(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validateAdmin(authentication);
        Page<AdminUserResponse> users = userService.getAdminUsers(page, size);
        long total = userService.countNonBotUsers();
        return ResponseEntity.ok(Map.of(
                "users", users,
                "totalUsers", total
        ));
    }

    @GetMapping("/admin/stats")
    public ResponseEntity<?> getAdminStats(Authentication authentication) {
        validateAdmin(authentication);
        long todayVisitors = visitorLogRepository.countByVisitDate(LocalDate.now());
        long totalVisitors = visitorLogRepository.count();
        long totalUsers = userService.countNonBotUsers();
        double conversionRate = totalVisitors == 0 ? 0.0
                : Math.round(totalUsers * 1000.0 / totalVisitors) / 10.0;
        return ResponseEntity.ok(Map.of(
                "todayVisitors", todayVisitors,
                "totalVisitors", totalVisitors,
                "totalUsers", totalUsers,
                "conversionRate", conversionRate
        ));
    }

    private void validateAdmin(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));
        if (!"ADMIN".equals(user.getRole())) throw new SecurityException("관리자 권한이 필요합니다");
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<String> handleSecurity(SecurityException e) {
        return ResponseEntity.status(403).body(e.getMessage());
    }
}
