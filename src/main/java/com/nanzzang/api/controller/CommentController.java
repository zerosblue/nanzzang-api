package com.nanzzang.api.controller;

import com.nanzzang.api.dto.CommentRequest;
import com.nanzzang.api.dto.CommentResponse;
import com.nanzzang.api.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    @GetMapping("/topic/{topicId}")
    public ResponseEntity<List<CommentResponse>> getCommentsByTopic(@PathVariable UUID topicId) {
        return ResponseEntity.ok(commentService.getCommentsByTopic(topicId));
    }

    @PostMapping("/topic/{topicId}")
    public ResponseEntity<CommentResponse> createComment(
            @PathVariable UUID topicId,
            Authentication authentication,
            @Valid @RequestBody CommentRequest request) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(commentService.createComment(topicId, userId, request));
    }

    @PostMapping("/{commentId}/like")
    public ResponseEntity<CommentResponse> toggleLike(@PathVariable UUID commentId) {
        return ResponseEntity.ok(commentService.toggleLike(commentId));
    }
}
