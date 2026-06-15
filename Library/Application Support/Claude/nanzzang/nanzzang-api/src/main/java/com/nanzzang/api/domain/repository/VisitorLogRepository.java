package com.nanzzang.api.domain.repository;

import com.nanzzang.api.domain.VisitorLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface VisitorLogRepository extends JpaRepository<VisitorLog, Long> {
    boolean existsByIpAddressAndVisitDate(String ipAddress, LocalDate visitDate);
    long countByVisitDate(LocalDate visitDate);

    List<VisitorLog> findByVisitDateGreaterThanEqual(LocalDate date);
}
