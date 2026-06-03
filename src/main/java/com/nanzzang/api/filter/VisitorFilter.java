package com.nanzzang.api.filter;

import com.nanzzang.api.domain.VisitorLog;
import com.nanzzang.api.domain.repository.VisitorLogRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDate;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisitorFilter extends OncePerRequestFilter {

    private final VisitorLogRepository visitorLogRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        // Only count GET requests to the main topics list (one entry-point per visit)
        return !("GET".equals(method) && "/api/v1/topics".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String ip = resolveIp(request);
            LocalDate today = LocalDate.now();
            if (!visitorLogRepository.existsByIpAddressAndVisitDate(ip, today)) {
                visitorLogRepository.save(new VisitorLog(ip, today));
            }
        } catch (Exception e) {
            log.warn("방문자 로그 저장 실패: {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
