package com.example.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AptitudeMockReviewItemDTO {
    private String id;
    private int order;
    private String question;
    private List<String> options;
    private String correctAnswer;
    private String chosenAnswer;
    private boolean correct;
    private String solution;
    private String trick;
}
