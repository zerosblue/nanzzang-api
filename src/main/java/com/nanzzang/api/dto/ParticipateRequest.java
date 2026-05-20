package com.nanzzang.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ParticipateRequest {

    @NotBlank(message = "팀 사이드는 필수입니다")
    @Pattern(regexp = "^[AB]$", message = "팀 사이드는 A 또는 B만 가능합니다")
    private String teamSide;
}
