package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * A single verbal / English topic (e.g. "Grammar", "Idioms & Phrases",
 * "Reading Comprehension").
 *
 * Verbal ability is rule- and usage-based — not formula based — so it uses a
 * purpose-built lesson model ({@link VerbalLesson}). It keeps the same two-mode
 * student experience as the other categories:
 *   - "Learn It" — understand the rule and how it works, with examples.
 *   - "Crack It" — exam-day strategies and signal-spotting.
 *
 * Metadata mirrors {@link AptitudeTopic} so the shared navigation works
 * identically; only the heavy {@code lesson} block is list-excluded.
 */
@Document(collection = "verbal_topics")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VerbalTopic {

    @Id
    private String id;

    // Always "verbal" — kept for parity with the shared navigation queries.
    private String category;

    private String group;         // slug of the group (category → group → topic)

    @Indexed(unique = true)
    private String topic;         // URL-safe slug — the :topicId route param

    private String displayName;
    private String description;
    private String icon;

    private String difficulty;    // easy | medium | hard
    private String priority;      // high | medium | low

    private int order;
    private boolean isActive;

    private VerbalLesson lesson;  // null until authored

    private List<AptitudeTopic.Video> videos;

    // ── The lesson: two student modes ───────────────────────────────────────
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class VerbalLesson {
        private Learn learn;   // MODE 1 — understand the rule from zero
        private Crack crack;   // MODE 2 — solve it fast in the exam
    }

    // ── MODE 1: Learn It ─────────────────────────────────────────────────────
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Learn {
        private String realLife;              // an everyday hook
        private String coreIdea;              // what this topic tests, plainly
        private String howItWorks;            // the method/approach in plain English
        private List<Rule> rules;             // the rules/strategies to know
        private List<WorkedType> workedTypes; // every question type, fully solved
        private List<String> traps;           // common mistakes to avoid
        private String memoryHook;            // one-line memory aid
    }

    /** A grammar rule / vocabulary strategy / usage principle (replaces quant "formulas"). */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Rule {
        private String name;         // short name for the rule
        private String rule;         // the rule itself, in one line
        private String explanation;  // why it is true / how to use it
        private String example;      // one clear example (right vs wrong where useful)
    }

    /** A single exam question pattern, explained and fully solved. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class WorkedType {
        private String name;
        private String idea;
        private String problem;
        private List<Step> steps;
        private String answer;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Step {
        private String action;
        private String why;
    }

    // ── MODE 2: Crack It ─────────────────────────────────────────────────────
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Crack {
        private String oneLine;
        private List<Strategy> strategies;     // one exam strategy per question type
        private List<SignalGuide> signalGuide; // signal word/clue → what it means
        private List<DrillItem> drill;         // 60-second self-test
        private String whyItWorks;
        private List<String> bestFor;
        private List<String> notFor;
        private String practicePattern;
    }

    /** A fast exam strategy for one question type. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Strategy {
        private String name;
        private String method;       // the strategy in one line
        private String howToApply;   // exactly how to run it
        private String example;      // the same example solved fast
        private String whenWorks;
        private String whenFails;
    }

    /** A signal word / clue → what it tells you. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class SignalGuide {
        private String signal;       // the exact word/phrase to spot
        private String use;          // what it signals / the move it triggers
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DrillItem {
        private String question;
        private String answer;
    }
}
