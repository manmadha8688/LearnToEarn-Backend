package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * A single aptitude topic (e.g. "Time, Speed & Distance").
 *
 * Everything except the 4 category cards lives here in the database — topic
 * metadata AND the two learning modes ("Learn It" full explanation + "Crack It"
 * shortcuts). Quiz questions are added later and are NOT modelled here yet.
 *
 * The heavy {@code learnIt}/{@code crackIt} blocks are excluded from the list
 * endpoints (see AptitudeTopicRepository) and only sent for a single topic.
 */
@Document(collection = "aptitude_topics")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AptitudeTopic {

    @Id
    private String id;

    // quantitative | logical | verbal | data-interpretation
    private String category;

    // URL-safe slug, unique per topic — used as the :topicId route param
    @Indexed(unique = true)
    private String topic;

    private String displayName;
    private String description;   // one line about this topic
    private String icon;          // emoji

    // easy | medium | hard
    private String difficulty;

    // high | medium | low — study priority for students (drives filtering + sort).
    // "high" = must-know / most-asked, "low" = advanced / rarely asked.
    private String priority;

    private int order;            // display order inside its category (syllabus order)
    private boolean isActive;

    // ── Learning modes (added to the DB; null until authored) ────────────────
    private LearnMode learnIt;    // MODE 1 — full, beginner-first explanation
    private CrackMode crackIt;    // MODE 2 — shortcut / topper methods

    // Two curated YouTube videos per topic ("Watch first").
    private List<Video> videos;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Video {
        private String label;   // video title
        private String url;     // YouTube link
        private String note;    // short note (channel / why watch) — optional
    }

    // ── MODE 1: Learn It ─────────────────────────────────────────────────────
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LearnMode {
        private String realLife;          // real-life example, before any math
        private String plainMeaning;      // what it means in plain English
        private String mathIntro;         // bridge into the math
        private List<Formula> formulas;   // each formula broken down word by word
        private List<WorkedExample> examples; // 3 examples: simple → medium → hard
        private List<String> commonMistakes;
        private String memoryTrick;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Formula {
        private String label;
        private String formula;
        private String breakdown;   // word-by-word, not just symbols
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class WorkedExample {
        private String level;       // Simple | Medium | Hard
        private String problem;
        private List<Step> steps;   // every single step
        private String answer;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Step {
        private String action;      // what to do ("do" is a reserved word)
        private String why;         // why this step is done
    }

    // ── MODE 2: Crack It ─────────────────────────────────────────────────────
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CrackMode {
        private String theTrick;              // one-sentence what the shortcut does
        private Shortcut shortcut;            // the shortcut method itself
        private List<CrackExample> examples;  // SAME 3 examples, shortcut-only
        private List<Comparison> comparison;  // normal (2 min) vs shortcut (20 sec)
        private String whyItWorks;            // 2 simple lines
        private List<String> whenWorks;
        private List<String> whenNot;
        private String practicePattern;       // the exact question type this solves
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Shortcut {
        private String formula;
        private String whereNumbersGo;   // exactly where each number goes
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class CrackExample {
        private String level;
        private String problem;
        private List<String> steps;   // short shortcut steps
        private String answer;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Comparison {
        private String example;
        private String normal;    // the long way (~2 min)
        private String shortcut;  // the fast way (~20 sec)
    }
}
