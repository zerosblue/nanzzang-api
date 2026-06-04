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

    @Query("SELECT v.visitDate, COUNT(v) FROM VisitorLog v WHERE v.visitDate >= :startDate GROUP BY v.visitDate ORDER BY v.visitDate")
    List<Object[]> findDailyCountsSince(@Param("startDate") LocalDate startDate);
}
