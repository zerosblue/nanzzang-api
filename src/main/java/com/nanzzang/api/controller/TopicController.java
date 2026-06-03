package com.nanzzang.api.controller;

import com.nanzzang.api.domain.User;
import com.nanzzang.api.domain.repository.UserRepository;
import com.nanzzang.api.dto.TopicRequest;
import com.nanzzang.api.dto.TopicResponse;
import com.nanzzang.api.dto.ParticipateRequest;
import com.nanzzang.api.service.TopicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;
    private final UserRepository userRepository;

    private void validateAdmin(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));
        if (!"ADMIN".equals(user.getRole())) {
            throw new SecurityException("관리자 권한이 필요합니다");
        }
    }

    @GetMapping
    public ResponseEntity<Page<TopicResponse>> getTopics(
            @RequestParam(defaultValue = "hot") String sort,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(topicService.getTopics(sort, category, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TopicResponse> getTopicDetail(@PathVariable UUID id) {
        // 조회수 증가 + 상세 반환
        return ResponseEntity.ok(topicService.incrementViewCount(id));
    }

    @PostMapping
    public ResponseEntity<TopicResponse> createTopic(
            Authentication authentication,
            @Valid @RequestBody TopicRequest request) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(topicService.createTopic(userId, request));
    }

    @PostMapping("/{id}/participate")
    public ResponseEntity<Void> participate(
            @PathVariable UUID id,
            Authentication authentication,
            @Valid @RequestBody ParticipateRequest request) {
        UUID userId = (UUID) authentication.getPrincipal();
        topicService.participate(id, userId, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/admin/all")
    public ResponseEntity<Page<TopicResponse>> getAdminTopics(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        validateAdmin(authentication);
        return ResponseEntity.ok(topicService.getTopics("latest", null, page, size));
    }

    @DeleteMapping("/admin/{id}")
    public ResponseEntity<Void> deleteTopic(Authentication authentication, @PathVariable UUID id) {
        validateAdmin(authentication);
        topicService.deleteTopic(id);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/admin/all")
    public ResponseEntity<Void> deleteAllTopics(Authentication authentication) {
        validateAdmin(authentication);
        topicService.deleteAllTopics();
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<String> handleSecurityException(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
}
