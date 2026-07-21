package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * A LeetCode-style coding problem for the coding platform.
 *
 * Kept deliberately separate from {@link ProblemQuestion} (the read-and-learn Code
 * Gym): this collection is write-and-run only, with a strict stdin→stdout judge,
 * split sample/hidden test cases, and per-problem language/time/memory limits.
 *
 * SECURITY: {@code hiddenTestCases} must NEVER be serialized to the client. The
 * public API returns a DTO built from this document with hidden cases stripped.
 */
@Document(collection = "coding_problems")
@Getter @Setter @NoArgsConstructor
public class CodingProblem {

    @Id
    private String id;

    private String title;
    private String difficulty;          // Easy | Medium | Hard
    private String category;            // "Arrays", "Strings", "Dynamic Programming"…
    private int orderIndex;             // sort order in the problem list

    private String description;
    private List<String> constraints;   // human-readable bullet lines

    private List<Example> examples;     // shown in the description (input/output/explanation)

    // Visible on the problem page — the student can see these inputs & run against them.
    private List<IOCase> sampleTestCases;
    // Never exposed to the client. Only pass/fail is returned on Submit.
    private List<IOCase> hiddenTestCases;

    private List<String> supportedLanguages; // subset of python/java/c/cpp
    private int timeLimit;                    // seconds (informational; engine caps at 5s)
    private int memoryLimit;                  // MB (informational)

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class Example {
        private String input;
        private String output;
        private String explanation;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class IOCase {
        private String input;           // fed to the program's stdin
        private String expectedOutput;  // compared (trimmed) against stdout
        private String label;           // e.g. "Example 1" (samples only; optional)
    }
}
