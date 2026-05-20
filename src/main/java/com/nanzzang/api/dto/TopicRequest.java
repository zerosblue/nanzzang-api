package com.nanzzang.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class TopicRequest {

    @NotBlank(message = "제목은 필수입니다")
    @Size(max = 100, message = "제목은 100자 이내로 작성해주세요")
    private String title;

    private String body;

    @NotBlank(message = "카테고리는 필수입니다")
    @Size(max = 30)
    private String category;

    @NotBlank(message = "A팀 이름은 필수입니다")
    @Size(max = 30)
    private String teamAName;

    @NotBlank(message = "B팀 이름은 필수입니다")
    @Size(max = 30)
    private String teamBName;

    private Integer durationDays; // 4 or 7

    @Size(max = 3, message = "이미지는 최대 3장까지 첨부할 수 있습니다")
    private List<String> imageUrls;
}
