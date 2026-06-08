package com.example.student.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @AllArgsConstructor @NoArgsConstructor
public class RoadmapDetailDTO {
    private String id;
    private String title;
    private String description;
    private String icon;
    private String color;
    private List<SubjectProgress> subjects;
    private int totalSubjects;
    private int completedSubjects;
    private double overallPercentage;
    private boolean enrolled;
    private boolean paused;
    private int estimatedWeeks;
    private String roleTarget;

    // Multiple target roles
    private List<String> roleTargets;

    // Rich info
    private String overview;
    private String whyLearn;
    private String forWho;
    private List<String> prerequisites;
    private List<String> toolsRequired;
    private List<String> outcomes;

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class SubjectProgress {
        private String id;
        private String title;
        private String icon;
        private String color;
        private int orderIndex;
        private int totalConcepts;
        private long completedConcepts;
        private double percentage;
        private boolean hasBadge;
    }
}
