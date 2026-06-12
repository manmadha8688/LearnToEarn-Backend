package com.example.student.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "problem_questions")
@Getter @Setter @NoArgsConstructor
public class ProblemQuestion {

    @Id
    private String id;

    // List so one question can appear in multiple tracks
    // e.g. ["SKILL_UP", "INTERVIEW_PREP"]
    private List<String> tracks;

    private List<String> topics;
    private String category;

    // BEGINNER | INTERMEDIATE | ADVANCED
    private String level;

    // CONCEPTUAL | OUTPUT | DEBUG | WRITE | PATTERN
    private String type;

    private String title;
    private String description;
    private String inputFormat;
    private String outputFormat;
    private String sampleInput;
    private String sampleOutput;
    private String example1Explanation;   // explains why Example 1 produces that output
    private String sampleInput2;
    private String sampleOutput2;
    private String example2Explanation;   // explains why Example 2 produces that output
    private String constraints;
    private String codeSnippet;

    private List<String> hints;
    private String approach;

    private Solutions solutions;

    private String explanation;
    private String interviewTip;

    @JsonProperty("isInterview")
    private Boolean isInterview;

    private List<String> companiesThatAsk;
    private int orderIndex;

    // ── Nested types ─────────────────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class Solutions {
        private SolutionVariant brute;
        private SolutionVariant normal;
        private SolutionVariant optimized;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class SolutionVariant {
        private String logic;
        private String timeComplexity;
        private String spaceComplexity;
        private SolutionCode code;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class SolutionCode {
        private String c;
        private String python;
        private String java;
        private String cpp;
    }
}
