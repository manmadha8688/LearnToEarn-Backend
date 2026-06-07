package com.example.student.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data @AllArgsConstructor @NoArgsConstructor
public class SubjectDTO {
    private String id;
    private String title;
    private String description;
    private String icon;
    private String color;
    private int totalConcepts;
    private long completedCount;
    private String rank;

    // Rich info (used in subject detail page)
    private String overview;
    private String whyLearn;
    private String forWho;
    private List<String> prerequisites;
    private List<String> outcomes;
    private List<String> whatYouWillBuild;
    private List<String> toolsRequired;
    private String difficulty;
    private int estimatedHours;
    private String careerUse;
}
