package com.example.student.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AptitudeMockSectionResultDTO {
    private String id;
    private String label;
    private String color;
    private int correct;
    private int total;
    private List<AptitudeMockReviewItemDTO> items;
}
