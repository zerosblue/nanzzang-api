package com.nanzzang.api.controller;

import com.nanzzang.api.domain.Topic;
import com.nanzzang.api.domain.User;
import com.nanzzang.api.domain.Comment;
import com.nanzzang.api.domain.VisitorLog;
import com.nanzzang.api.domain.repository.CommentRepository;
import com.nanzzang.api.domain.repository.TopicRepository;
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

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserRepository userRepository;
    private final VisitorLogRepository visitorLogRepository;
    private final TopicRepository topicRepository;
    private final CommentRepository commentRepository;

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

    @GetMapping("/admin/daily-visitors")
    public ResponseEntity<?> getDailyVisitors(
            Authentication authentication,
            @RequestParam(defaultValue = "30") int days) {
        validateAdmin(authentication);
        LocalDate startDate = LocalDate.now().minusDays(days - 1);
        Map<String, Long> countMap = visitorLogRepository.findByVisitDateGreaterThanEqual(startDate)
                .stream()
                .collect(Collectors.groupingBy(
                        log -> log.getVisitDate().format(DateTimeFormatter.ISO_LOCAL_DATE),
                        Collectors.counting()
                ));

        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = days - 1; i >= 0; i--) {
            String date = LocalDate.now().minusDays(i).format(DateTimeFormatter.ISO_LOCAL_DATE);
            result.add(Map.of("date", date, "count", countMap.getOrDefault(date, 0L)));
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/admin/{userId}/activity")
    @Transactional(readOnly = true)
    public ResponseEntity<?> getUserActivity(
            Authentication authentication,
            @PathVariable UUID userId) {
        validateAdmin(authentication);

        List<Topic> topics = topicRepository.findByAuthorIdOrderByCreatedAtDesc(userId);
        List<Comment> comments = commentRepository.findByUserIdOrderByCreatedAtDesc(userId);

        List<Map<String, Object>> topicList = topics.stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("title", t.getTitle());
            m.put("category", t.getCategory());
            m.put("viewCount", t.getViewCount());
            m.put("createdAt", t.getCreatedAt());
            return m;
        }).collect(Collectors.toList());

        List<Map<String, Object>> commentList = comments.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("content", c.getContent());
            m.put("teamSide", c.getTeamSide());
            m.put("topicId", c.getTopic().getId());
            m.put("topicTitle", c.getTopic().getTitle());
            m.put("createdAt", c.getCreatedAt());
            return m;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(Map.of("topics", topicList, "comments", commentList));
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
