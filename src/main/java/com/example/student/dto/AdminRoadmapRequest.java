package com.example.student.dto;

import lombok.Data;

@Data
public class AdminRoadmapRequest {
    private String title;
    private String description;
    private String roleTarget;
    private String icon;
    private String color;
    private int estimatedWeeks;
}
