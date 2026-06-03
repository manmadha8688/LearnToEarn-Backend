package com.example.student.dto;

import lombok.Data;
import java.util.List;

@Data
public class AdminSubjectRequest {
    private String title;
    private String description;
    private String icon;
    private String color;
    private String rank;

    // Rich info
    private String overview;
    private String whyLearn;
    private String forWho;
    private List<String> prerequisites;
    private List<String> outcomes;
    private List<String> whatYouWillBuild;
    private String difficulty;
    private int estimatedHours;
    private String careerUse;
}
