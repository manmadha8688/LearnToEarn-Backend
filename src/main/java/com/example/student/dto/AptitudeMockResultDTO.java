package com.example.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AptitudeMockResultDTO {
    private String attemptId;
    private int correct;
    private int total;
    private int percentage;
    private int passMark;
    private boolean passed;
    private int xpEarned;
    private LocalDateTime nextRetryAt;
    private List<AptitudeMockSectionResultDTO> sections;
}
