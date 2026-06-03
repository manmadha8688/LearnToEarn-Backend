package com.example.student.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @AllArgsConstructor @NoArgsConstructor
public class QuizAttemptResponse {
    private boolean canRetry;
    private boolean hasPassed;
    private int bestScore;
    private int bestTotal;
    private long attemptCount;
    private LocalDateTime nextRetryAt;
    private LocalDateTime lastAttemptAt;
}
