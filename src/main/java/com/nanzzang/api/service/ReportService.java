package com.nanzzang.api.service;

import com.nanzzang.api.domain.Comment;
import com.nanzzang.api.domain.Report;
import com.nanzzang.api.domain.Topic;
import com.nanzzang.api.domain.User;
import com.nanzzang.api.domain.repository.CommentRepository;
import com.nanzzang.api.domain.repository.ReportRepository;
import com.nanzzang.api.domain.repository.TopicRepository;
import com.nanzzang.api.domain.repository.UserRepository;
import com.nanzzang.api.dto.ReportRequest;
import com.nanzzang.api.dto.ReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final TopicRepository topicRepository;
    private final CommentRepository commentRepository;

    @Transactional
    public void createReport(UUID reporterId, ReportRequest request) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 사용자입니다"));

        Report report = Report.builder()
                .reporter(reporter)
                .targetType(request.getTargetType())
                .targetId(request.getTargetId())
                .reason(request.getReason())
                .build();

        reportRepository.save(report);
    }

    public List<ReportResponse> getPendingReports() {
        List<Report> reports = reportRepository.findByIsResolvedFalseOrderByCreatedAtDesc();
        List<ReportResponse> responses = new ArrayList<>();

        for (Report r : reports) {
            String snippet = "[삭제됨 또는 알 수 없음]";
            try {
                if ("TOPIC".equalsIgnoreCase(r.getTargetType())) {
                    Topic topic = topicRepository.findById(r.getTargetId()).orElse(null);
                    if (topic != null) {
                        snippet = "토픽: " + topic.getTitle();
                    }
                } else if ("COMMENT".equalsIgnoreCase(r.getTargetType())) {
                    Comment comment = commentRepository.findById(r.getTargetId()).orElse(null);
                    if (comment != null) {
                        snippet = "댓글: " + comment.getContent();
                    }
                }
            } catch (Exception e) {
                // Ignore and keep default snippet
            }
            responses.add(ReportResponse.of(r, snippet));
        }

        return responses;
    }

    @Transactional
    public void resolveReport(UUID reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 신고입니다"));
        report.resolveReport();
        reportRepository.save(report);
    }

    @Transactional
    public void deleteTarget(UUID reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 신고입니다"));

        if ("TOPIC".equalsIgnoreCase(report.getTargetType())) {
            topicRepository.findById(report.getTargetId())
                    .ifPresent(topicRepository::delete);
        } else if ("COMMENT".equalsIgnoreCase(report.getTargetType())) {
            commentRepository.findById(report.getTargetId())
                    .ifPresent(commentRepository::delete);
        }

        report.resolveReport();
        reportRepository.save(report);
    }
}
