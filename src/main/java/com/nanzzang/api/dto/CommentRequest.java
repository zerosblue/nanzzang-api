package com.nanzzang.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
public class CommentRequest {

    @NotBlank(message = "댓글 내용은 필수입니다")
    private String content;

    @NotBlank(message = "팀 사이드는 필수입니다")
    @Pattern(regexp = "^[AB]$", message = "팀 사이드는 A 또는 B만 가능합니다")
    private String teamSide;

    private UUID parentId; // null이면 최상위 댓글
}
