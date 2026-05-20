package com.nanzzang.api.dto;

import com.nanzzang.api.domain.Report;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class ReportResponse {
    private UUID id;
    private String reporterNickname;
    private String targetType;
    private UUID targetId;
    private String targetSnippet; // 신고 대상 글의 일부 내용 (화면 표기용)
    private String reason;
    private boolean isResolved;
    private LocalDateTime createdAt;

    public static ReportResponse of(Report report, String targetSnippet) {
        return ReportResponse.builder()
                .id(report.getId())
                .reporterNickname(report.getReporter().getNickname())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .targetSnippet(targetSnippet)
                .reason(report.getReason())
                .isResolved(report.isResolved())
                .createdAt(report.getCreatedAt())
                .build();
    }
}
