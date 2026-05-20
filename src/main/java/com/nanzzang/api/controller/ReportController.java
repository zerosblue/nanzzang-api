package com.nanzzang.api.controller;

import com.nanzzang.api.domain.User;
import com.nanzzang.api.domain.repository.UserRepository;
import com.nanzzang.api.dto.ReportRequest;
import com.nanzzang.api.dto.ReportResponse;
import com.nanzzang.api.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;
    private final UserRepository userRepository;

    private void validateAdmin(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));
        if (!"ADMIN".equals(user.getRole())) {
            throw new SecurityException("관리자 권한이 필요합니다");
        }
    }

    // 1. 일반 유저 신고 제출 API
    @PostMapping("/reports")
    public ResponseEntity<Void> createReport(Authentication authentication, @RequestBody ReportRequest request) {
        UUID reporterId = (UUID) authentication.getPrincipal();
        reportService.createReport(reporterId, request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // 2. 관리자 신고 대기 목록 조회 API
    @GetMapping("/admin/reports")
    public ResponseEntity<List<ReportResponse>> getPendingReports(Authentication authentication) {
        validateAdmin(authentication);
        return ResponseEntity.ok(reportService.getPendingReports());
    }

    // 3. 관리자 신고 기각/반려 (해당 신고만 완료 처리) API
    @PostMapping("/admin/reports/{id}/resolve")
    public ResponseEntity<Void> resolveReport(Authentication authentication, @PathVariable UUID id) {
        validateAdmin(authentication);
        reportService.resolveReport(id);
        return ResponseEntity.ok().build();
    }

    // 4. 관리자 신고 접수 및 게시글 삭제 API
    @PostMapping("/admin/reports/{id}/delete")
    public ResponseEntity<Void> deleteTarget(Authentication authentication, @PathVariable UUID id) {
        validateAdmin(authentication);
        reportService.deleteTarget(id);
        return ResponseEntity.ok().build();
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<String> handleSecurityException(SecurityException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
}
