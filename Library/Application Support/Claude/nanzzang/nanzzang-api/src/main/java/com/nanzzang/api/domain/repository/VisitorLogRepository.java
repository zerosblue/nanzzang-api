package com.nanzzang.api.domain.repository;

import com.nanzzang.api.domain.VisitorLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface VisitorLogRepository extends JpaRepository<VisitorLog, Long> {
    boolean existsByIpAddressAndVisitDate(String ipAddress, LocalDate visitDate);
    long countByVisitDate(LocalDate visitDate);

    @Query(value = "SELECT visit_date, COUNT(*) FROM visitor_logs WHERE visit_date >= :startDate GROUP BY visit_date ORDER BY visit_date", nativeQuery = true)
    List<Object[]> findDailyCountsSince(@Param("startDate") LocalDate startDate);
}
