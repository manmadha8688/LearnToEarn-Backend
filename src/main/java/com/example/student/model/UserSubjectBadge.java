package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "user_subject_badges")
@CompoundIndex(def = "{'userId': 1, 'subjectId': 1}", unique = true)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserSubjectBadge {

    @Id
    private String id;

    private String userId;
    private String subjectId;
    private int score;
    private int total;
    private LocalDateTime earnedAt;
}
