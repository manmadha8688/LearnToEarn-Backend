package com.example.student.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Slim list payload for GET /subjects — card + About-modal fields only.
 * Detail endpoint still returns the full subject document with concepts.
 */
@Data @AllArgsConstructor @NoArgsConstructor
public class SubjectListDTO {
    private String id;
    private String title;
    private String icon;
    private String color;
    private int totalConcepts;
    private long completedCount;
    private String rank;

    // About popup (opened from list cards — no second fetch)
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
