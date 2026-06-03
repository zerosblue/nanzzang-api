package com.nanzzang.api.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "visitor_logs", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"ip_address", "visit_date"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitorLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ip_address", nullable = false, length = 64)
    private String ipAddress;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    public VisitorLog(String ipAddress, LocalDate visitDate) {
        this.ipAddress = ipAddress;
        this.visitDate = visitDate;
    }
}
