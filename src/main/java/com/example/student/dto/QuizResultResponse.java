package com.example.student.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data @AllArgsConstructor @NoArgsConstructor
public class QuizResultResponse {
    private String attemptId;
    private int score;
    private int total;
    private boolean passed;
    private String badge;
    private LocalDateTime nextRetryAt;
    private List<QuizAnswerResult> results;
    // XP awarded on this submission (concept quiz only)
    private int  xpEarned;
    private boolean dailyBonusEarned;
}
