package com.nanzzang.api.controller;

import com.nanzzang.api.dto.TopicRequest;
import com.nanzzang.api.dto.TopicResponse;
import com.nanzzang.api.dto.ParticipateRequest;
import com.nanzzang.api.service.TopicService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/topics")
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

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
}
