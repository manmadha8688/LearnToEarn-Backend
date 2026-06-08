package com.example.student.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @NoArgsConstructor
public class RoadmapListDTO {
    private String id;
    private String title;
    private String description;
    private String roleTarget;
    private String icon;
    private String color;
    private int estimatedWeeks;
    private int subjectCount;
    private boolean enrolled;
    private boolean paused;
    private boolean allSubjectsDone;
    // Rich info — included so the About popup is instant, no second API call needed
    private List<String> roleTargets;
    private String overview;
    private String whyLearn;
    private String forWho;
    private List<String> prerequisites;
    private List<String> toolsRequired;
    private List<String> outcomes;
}
