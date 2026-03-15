package com.historyTalk.dto.quiz;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateQuizRequest {

    private String title;

    private String description;

    private Integer grade;

    private Integer chapterNumber;

    private String chapterTitle;

    private String era;

    private Integer durationSeconds;

}
