package com.example.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AptitudeMockSectionDTO {
    private String id;
    private String label;
    private String icon;
    private String color;
    private int timeSeconds;
    private int questionCount;
    private List<AptitudeMockQuestionDTO> questions;
}
