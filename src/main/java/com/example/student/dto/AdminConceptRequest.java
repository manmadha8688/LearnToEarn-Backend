package com.example.student.dto;

import com.example.student.model.Concept;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AdminConceptRequest {
    @NotBlank
    private String subjectId;

    @NotBlank
    @Size(max = 300)
    private String title;

    // rich content
    @Size(max = 20000)
    private String introduction;
    @Size(max = 20000)
    private String explanationSimple;
    @Size(max = 20000)
    private String explanationTechnical;
    @Size(max = 20000)
    private String syntax;
    private List<Concept.ConceptExample> examples;
    private List<String> keyPoints;
    @Size(max = 2000)
    private String tip;
    private List<String> commonMistakes;
    private List<Concept.TrickyProblem> trickyProblems;

    @Size(max = 10)
    private String rank;
    @Min(0)
    @Max(100000)
    private int estimatedMinutes;
    @Min(0)
    private int orderIndex;

    // video
    @Size(max = 500)
    private String videoUrl;
    @Size(max = 300)
    private String videoTitle;
}
