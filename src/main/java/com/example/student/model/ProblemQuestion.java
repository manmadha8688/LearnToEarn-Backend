package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * A single Code Gym coding problem.
 *
 * Redesigned schema (v2):
 *  - one problem belongs to exactly ONE track (no multi-track membership)
 *  - examples are a structured list ({ input, output, explanation })
 *  - solution variants (brute / normal / optimized) are all OPTIONAL — a simple
 *    beginner problem may ship just one; harder ones ship all three
 *  - no interview/company fields, no free-form "type" — the app has no code
 *    editor, so problems are read-and-learn, not write-and-run
 */
@Document(collection = "problem_questions")
@Getter @Setter @NoArgsConstructor
public class ProblemQuestion {

    @Id
    private String id;

    // ── Placement within the gym ──────────────────────────────────────────────
    // START_CODING | LOGIC_BUILDING | SKILL_UP | CRACK_IT | BUILD_IT | PROVE_IT
    private String track;
    private int orderIndex;

    // BEGINNER | INTERMEDIATE | ADVANCED  (difficulty filter inside a track)
    private String level;

    private String category;        // group heading inside a track ("Loops", "Patterns"…)
    private List<String> topics;    // tags for filtering

    // ── Statement ──────────────────────────────────────────────────────────────
    private String title;
    private String description;
    private String inputFormat;
    private String outputFormat;
    private String constraints;
    private String codeSnippet;     // optional — shown for "read this code" style problems

    // ── Examples (usually 2) ─────────────────────────────────────────────────────
    private List<Example> examples;

    // ── Guidance ─────────────────────────────────────────────────────────────────
    private List<String> hints;
    private String approach;             // how to think — a guide, not the answer
    private List<String> whatYouLearn;   // the skills/concepts this problem builds

    // ── Solutions (each variant optional) ────────────────────────────────────────
    private Solutions solutions;

    // ── Wrap-up ──────────────────────────────────────────────────────────────────
    private String explanation;     // why the solution works
    private String tip;             // short encouraging tip for the student

    // ── Nested types ─────────────────────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class Example {
        private String input;
        private String output;
        private String explanation;
    }

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
