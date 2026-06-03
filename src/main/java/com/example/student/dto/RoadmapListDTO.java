package com.example.student.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @AllArgsConstructor @NoArgsConstructor
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
}
