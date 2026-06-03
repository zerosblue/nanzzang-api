package com.nanzzang.api.controller;

import com.nanzzang.api.domain.User;
import com.nanzzang.api.domain.repository.UserRepository;
import com.nanzzang.api.dto.CommentRequest;
import com.nanzzang.api.dto.CommentResponse;
import com.nanzzang.api.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    private final UserRepository userRepository;

    private void validateAdmin(Authentication auth) {
        UUID userId = (UUID) auth.getPrincipal();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));
        if (!"ADMIN".equals(user.getRole())) throw new SecurityException("관리자 권한이 필요합니다");
    }

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
    public ResponseEntity<CommentResponse> toggleLike(@PathVariable UUID commentId, Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ResponseEntity.ok(commentService.toggleLike(commentId, userId));
    }

    @DeleteMapping("/admin/{commentId}")
    public ResponseEntity<Void> adminDeleteComment(Authentication authentication, @PathVariable UUID commentId) {
        validateAdmin(authentication);
        commentService.deleteComment(commentId);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<String> handleSecurity(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
}
