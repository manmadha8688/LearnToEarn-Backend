package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "quiz_attempts")
@CompoundIndex(def = "{'userId': 1, 'type': 1, 'refId': 1}")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class QuizAttempt {

    @Id
    private String id;

    private String userId;
    private String type;     // CONCEPT | SUBJECT | ROADMAP
    private String refId;    // conceptId / subjectId / roadmapId

    private int score;
    private int total;
    private boolean passed;

    private int xpEarned;
    private boolean dailyBonusEarned;

    private LocalDateTime takenAt;
    private LocalDateTime nextRetryAt;
}
