package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * A single Code Gym coding problem.
 *
 * Redesigned schema (v2):
 *  - one problem belongs to exactly ONE track (no multi-track membership)
 *  - examples are a structured list ({ input, output, explanation })
 *  - solution variants (brute / normal / optimized) are all OPTIONAL — a simple
 *    beginner problem may ship just one; harder ones ship all three
 *  - when {@code testCases} is non-empty the problem is write-and-run (Monaco
 *    editor + server-side judge); otherwise it stays read-and-learn
 */
@Document(collection = "problem_questions")
@Getter @Setter @NoArgsConstructor
public class ProblemQuestion {

    @Id
    private String id;

    // ── Placement within the gym ──────────────────────────────────────────────
    // START_CODING | LOGIC_BUILDING | SKILL_UP | CRACK_IT | BUILD_IT | PROVE_IT
    @Indexed
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

    // ── Judge (optional) ─────────────────────────────────────────────────────────
    // When testCases is non-empty the problem becomes "write-and-run": the student
    // gets a code editor judged by stdin→stdout comparison. `sample` cases are
    // shown to the student (visible examples / Run); the rest are hidden (Submit).
    private List<TestCase> testCases;
    // Optional per-language editor starter; falls back to a generic template.
    private SolutionCode starterCode;

    // ── Solutions (each variant optional) ────────────────────────────────────────
    private Solutions solutions;

    // ── Wrap-up ──────────────────────────────────────────────────────────────────
    private String explanation;     // why the solution works
    private String tip;             // short encouraging tip for the student

    // ── Derived judge input ──────────────────────────────────────────────────────

    /** True for "read the code" conceptual problems (a snippet to trace, no editor/judge). */
    public boolean isReadOnlySnippet() {
        return codeSnippet != null && !codeSnippet.isBlank();
    }

    /**
     * Cases the judge should actually run. Visible sample cases come from the authored
     * {@code examples} (promoted to sample cases) unless the author explicitly stored sample
     * test cases; hidden cases come from {@code testCases}. So a problem can ship just worked
     * examples (2 samples, no hidden), or examples + N hidden {@code testCases} (2 samples +
     * N hidden) without duplicating the samples in {@code testCases}. Read-the-code snippet
     * problems never derive sample cases. Returns an empty list when nothing is judgeable.
     */
    public List<TestCase> effectiveTestCases() {
        List<TestCase> authored = (testCases == null) ? List.of() : testCases;
        boolean hasAuthoredSample = false;
        for (TestCase t : authored) {
            if (t != null && t.isSample()) { hasAuthoredSample = true; break; }
        }

        List<TestCase> out = new ArrayList<>();
        // Promote authored examples to visible sample cases unless explicit sample cases exist.
        if (!hasAuthoredSample && !isReadOnlySnippet() && examples != null) {
            for (Example ex : examples) {
                if (ex == null) continue;
                if (ex.getInput() == null && ex.getOutput() == null) continue;
                out.add(new TestCase(
                        ex.getInput() == null ? "" : ex.getInput(),
                        ex.getOutput() == null ? "" : ex.getOutput(),
                        true));
            }
        }
        for (TestCase t : authored) {
            if (t != null) out.add(t);
        }
        return out;
    }

    // ── Nested types ─────────────────────────────────────────────────────────────

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class Example {
        private String input;
        private String output;
        private String explanation;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class TestCase {
        private String input;           // fed to the program's stdin
        private String expectedOutput;  // compared (trimmed) against stdout
        private boolean sample;         // true → visible to the student (Run); false → hidden (Submit)
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
