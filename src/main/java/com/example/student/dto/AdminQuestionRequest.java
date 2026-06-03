package com.example.student.dto;

import lombok.Data;

import java.util.List;

@Data
public class AdminQuestionRequest {
    private String conceptId;
    private String subjectId;
    private String text;
    private List<String> options;
    private int correctIndex;
    private String explanation;
    private String difficulty;
}
