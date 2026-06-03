package com.example.student.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @AllArgsConstructor @NoArgsConstructor
public class QuizStartResponse {
    private String quizId;
    private String type;
    private String refId;
    private int totalQuestions;
    private Integer timeLimitMinutes;
    private List<QuizQuestionDTO> questions;
}
