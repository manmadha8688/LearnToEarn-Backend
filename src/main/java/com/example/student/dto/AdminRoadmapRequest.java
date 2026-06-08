package com.example.student.dto;

import lombok.Data;
import java.util.List;

@Data
public class AdminRoadmapRequest {
    private String title;
    private String description;
    private String roleTarget;
    private String icon;
    private String color;
    private int estimatedWeeks;
    // Multiple target roles
    private List<String> roleTargets;

    // Rich info
    private String overview;
    private String whyLearn;
    private String forWho;
    private List<String> prerequisites;
    private List<String> toolsRequired;
    private List<String> outcomes;
}
