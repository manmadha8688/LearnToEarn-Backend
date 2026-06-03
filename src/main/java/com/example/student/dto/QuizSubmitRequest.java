package com.example.student.dto;

import lombok.Data;

import java.util.List;

@Data
public class QuizSubmitRequest {
    private String type;
    private String refId;
    private List<String> questionIds;
    private List<Integer> answers;
}
