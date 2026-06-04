package com.nanzzang.api.filter;

import com.nanzzang.api.domain.User;
import com.nanzzang.api.domain.VisitorLog;
import com.nanzzang.api.domain.repository.UserRepository;
import com.nanzzang.api.domain.repository.VisitorLogRepository;
import com.nanzzang.api.security.JwtProvider;
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
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class VisitorFilter extends OncePerRequestFilter {

    private final VisitorLogRepository visitorLogRepository;
    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        String method = request.getMethod();
        return !("GET".equals(method) && "/api/v1/topics".equals(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            if (!isAdminRequest(request)) {
                String ip = resolveIp(request);
                LocalDate today = LocalDate.now();
                if (!visitorLogRepository.existsByIpAddressAndVisitDate(ip, today)) {
                    visitorLogRepository.save(new VisitorLog(ip, today));
                }
            }
        } catch (Exception e) {
            log.warn("방문자 로그 저장 실패: {}", e.getMessage());
        }
        filterChain.doFilter(request, response);
    }

    private boolean isAdminRequest(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) return false;
        String token = header.substring(7);
        if (!jwtProvider.validateToken(token)) return false;
        try {
            UUID userId = jwtProvider.getUserIdFromToken(token);
            return userRepository.findById(userId)
                    .map(user -> "ADMIN".equals(user.getRole()))
                    .orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    private String resolveIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
