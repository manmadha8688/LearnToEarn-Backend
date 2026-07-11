package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * A practice question for an aptitude topic — kept in its own collection so a
 * topic can hold many questions without bloating the topic document.
 *
 * The {@code _id} is deterministic ({topic}-q{order}) so re-seeding is idempotent.
 * Seeded from resources/seed/aptitude-questions.json. No user attempts are stored
 * here yet — this is content only.
 */
@Document(collection = "aptitude_questions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AptitudeQuestion {

    @Id
    private String id;            // deterministic: "{topic}-q{order}"

    @Indexed
    private String topic;         // topic slug this question belongs to
    private String category;
    private String group;

    private int order;            // 1..N — display order and difficulty ramp
    private String difficulty;    // easy | medium | hard

    private String question;
    private List<String> options; // exactly 4
    private String answer;        // "A" | "B" | "C" | "D"
    private String solution;      // full step-by-step explanation
    private String trick;         // one-line shortcut (optional)
    private String type;          // which Mode-1 question type this tests

    private boolean isActive;
}
