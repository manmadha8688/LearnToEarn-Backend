package com.example.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AptitudeMockPaperDTO {
    private int overallTimeSeconds;
    private int totalQuestions;
    private List<AptitudeMockSectionDTO> sections;
}
