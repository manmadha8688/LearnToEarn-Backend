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

    // Slug of the group this topic belongs to (e.g. "number-basics").
    // A group nests topics under a category: category → group → topic.
    private String group;

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

    private int order;            // display order inside its group (syllabus order)
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
        private List<WorkedExample> examples; // (legacy) simple → medium → hard examples
        private List<QuestionType> questionTypes; // every exam question pattern, fully solved
        private List<String> commonMistakes;
        private String memoryTrick;
    }

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Formula {
        private String label;
        private String formula;
        private String breakdown;   // what each part means + why it is true
        private String example;     // one worked number example
    }

    /** A single exam question pattern, explained and fully solved. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class QuestionType {
        private String name;        // clear name for the pattern
        private String idea;        // what makes it different + what it gives/asks
        private String problem;     // a real example with numbers
        private List<Step> steps;   // every step, with the reason
        private String answer;
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
        private String oneLine;               // the whole topic in one memorable line
        private String theTrick;              // (legacy) one-sentence what the shortcut does
        private Shortcut shortcut;            // (legacy) single shortcut method
        private List<ShortcutType> shortcuts; // one shortcut per question type
        private List<PatternClue> patternGuide; // read-the-question → pick-the-trick
        private List<DrillItem> drill;        // 60-second self-test
        private List<CrackExample> examples;  // (legacy) shortcut-only examples
        private List<Comparison> comparison;  // (legacy) normal vs shortcut
        private String whyItWorks;            // 2 simple lines
        private List<String> whenWorks;
        private List<String> whenNot;
        private String practicePattern;       // the exact question type this solves
    }

    /** A shortcut for one question type — with where numbers go, timing and limits. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ShortcutType {
        private String name;             // matches a Mode-1 question type
        private String method;           // the shortcut in one line
        private String whereNumbersGo;   // exactly where each number from the question goes
        private String example;          // the same example solved with the shortcut only
        private String timeSaved;        // normal X steps vs shortcut Y steps
        private String whenWorks;
        private String whenFails;
    }

    /** Read-the-question signal → which trick to use. */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class PatternClue {
        private String signal;   // the exact words/phrases in the question
        private String use;      // the trick to reach for
    }

    /** One quick self-test item (question + final answer only). */
    @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
    public static class DrillItem {
        private String question;
        private String answer;
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
