package com.example.student.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class AdminRoadmapRequest {
    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 2000)
    private String description;

    @Size(max = 100)
    private String roleTarget;

    @Size(max = 100)
    private String icon;

    @Size(max = 50)
    private String color;

    @Min(0)
    @Max(520)
    private int estimatedWeeks;
    // Multiple target roles
    private List<String> roleTargets;

    // Rich info
    @Size(max = 10000)
    private String overview;
    @Size(max = 10000)
    private String whyLearn;
    @Size(max = 2000)
    private String forWho;
    private List<String> prerequisites;
    private List<String> toolsRequired;
    private List<String> outcomes;
}
