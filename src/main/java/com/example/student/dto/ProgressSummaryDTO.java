package com.example.student.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @AllArgsConstructor @NoArgsConstructor
public class ProgressSummaryDTO {
    private long    totalConcepts;
    private long    completedConcepts;
    private double  percentage;
    private int     streak;
    private long    xp;
    private int     level;
    private String  rank;
    private boolean completedConceptToday;
    private List<SubjectProgress> subjectProgress;

    @Data @AllArgsConstructor @NoArgsConstructor
    public static class SubjectProgress {
        private String  subjectId;
        private String  title;
        private String  icon;
        private String  color;
        private String  rank;
        private int     total;
        private long    completed;
        private double  percentage;
        private boolean hasBadge;
    }
}
