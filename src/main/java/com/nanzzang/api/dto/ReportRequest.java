package com.nanzzang.api.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class ReportRequest {
    private String targetType; // "TOPIC" or "COMMENT"
    private UUID targetId;
    private String reason;
}
