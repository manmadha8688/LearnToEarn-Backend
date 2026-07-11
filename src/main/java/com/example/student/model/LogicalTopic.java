package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * A single logical-reasoning topic (e.g. "Blood Relations", "Syllogisms").
 *
 * Logical reasoning is puzzle/pattern based — NOT formula based — so it uses a
 * purpose-built lesson model ({@link LogicalLesson}) instead of the quantitative
 * one. The student-facing shape keeps the same two-mode idea as quantitative:
 *   - "Understand It" — read the pattern, learn how to think.
 *   - "Crack It"      — exam-day techniques and clue-spotting.
 *
 * Metadata mirrors {@link AptitudeTopic} so the shared navigation (categories →
 * groups → topics) works identically. Only the heavy {@code lesson} block is
 * excluded from list endpoints and sent for a single topic.
 */
@Document(collection = "logical_topics")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LogicalTopic {

    @Id
    private String id;

    // Always "logical" — kept for parity with the shared navigation queries.
    private String category;

    // Slug of the group this topic belongs to (category → group → topic).
    private String group;

    @Indexed(unique = true)
    private String topic;         // URL-safe slug — the :topicId route param

    private String displayName;
    private String description;   // one line about this topic
    private String icon;          // emoji

    private String difficulty;    // easy | medium | hard
    private String priority;      // high | medium | low — study priority

    private int order;            // display order inside its group
    private boolean isActive;

    // Purpose-built lesson (null until authored).
    private LogicalLesson lesson;

    // Two curated YouTube videos ("Watch first").
    private List<AptitudeTopic.Video> videos;

    // ── The lesson: two student modes ───────────────────────────────────────
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class LogicalLesson {
        private Understand understand;   // MODE 1 — learn the pattern from zero
        private Crack crack;             // MODE 2 — solve it fast in the exam
    }

    // ── MODE 1: Understand It ────────────────────────────────────────────────
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Understand {
        private String realLife;                 // an everyday hook, before any jargon
        private String coreIdea;                 // what this topic actually asks, plainly
        private String howToThink;               // the step-by-step mental approach
        private List<Pattern> patterns;          // the recurring patterns/rules to know
        private List<WorkedType> workedTypes;    // every question type, fully solved
        private List<String> traps;              // common mistakes to avoid
        private String memoryHook;               // one-line memory aid
    }

    /** A recurring logical pattern/rule (replaces quant "formulas"). */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Pattern {
        private String name;            // clear name for the pattern
        private String whatItLooksLike; // how you recognise it in a question
        private String howItWorks;      // the underlying logic
        private String example;         // one concrete worked example
    }

    /** A single exam question pattern, explained and fully solved. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class WorkedType {
        private String name;         // clear name for the pattern
        private String idea;         // what makes it different + what it gives/asks
        private String problem;      // a real example
        private List<Step> steps;    // every step, with the reason
        private String answer;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Step {
        private String action;       // what to do
        private String why;          // why this step is done
    }

    // ── MODE 2: Crack It ─────────────────────────────────────────────────────
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Crack {
        private String oneLine;                  // the whole topic in one memorable line
        private List<Technique> techniques;      // one fast method per question type
        private List<ClueMethod> clueToMethod;   // read-the-question → pick-the-method
        private List<DrillItem> drill;           // 60-second self-test
        private String whyItWorks;               // 2 simple lines
        private List<String> bestFor;            // when this approach shines
        private List<String> notFor;             // when to be careful / fall back
        private String practicePattern;          // the exact question type this solves
    }

    /** A fast method for one question type. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Technique {
        private String name;         // matches an "Understand It" question type
        private String method;       // the technique in one line
        private String howToApply;   // exactly how to run it on a question
        private String example;      // the same example solved fast
        private String timeSaved;    // e.g. "diagram once vs re-reading 4 times"
        private String whenWorks;
        private String whenFails;
    }

    /** Read-the-question clue → which method to use. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ClueMethod {
        private String clue;         // the exact words/setup in the question
        private String method;       // the method to reach for
    }

    /** One quick self-test item (question + final answer only). */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DrillItem {
        private String question;
        private String answer;
    }
}
