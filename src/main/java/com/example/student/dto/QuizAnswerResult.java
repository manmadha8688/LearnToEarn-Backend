package com.example.student.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @AllArgsConstructor @NoArgsConstructor
public class QuizAnswerResult {
    private String questionId;
    private String text;
    private List<String> options;
    private int studentAnswer;
    private int correctIndex;
    private boolean correct;
    private String explanation;
}
