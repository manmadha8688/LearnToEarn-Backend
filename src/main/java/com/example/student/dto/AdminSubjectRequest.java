package com.example.student.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class AdminSubjectRequest {
    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 2000)
    private String description;

    @Size(max = 100)
    private String icon;

    @Size(max = 50)
    private String color;

    @Size(max = 10)
    private String rank;

    // Rich info
    @Size(max = 10000)
    private String overview;
    @Size(max = 10000)
    private String whyLearn;
    @Size(max = 2000)
    private String forWho;
    private List<String> prerequisites;
    private List<String> outcomes;
    private List<String> whatYouWillBuild;
    private List<String> toolsRequired;
    @Size(max = 50)
    private String difficulty;
    @Min(0)
    @Max(10000)
    private int estimatedHours;
    @Size(max = 2000)
    private String careerUse;
}
