package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "user_concept_progress")
@CompoundIndex(def = "{'userId': 1, 'conceptId': 1}", unique = true)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserConceptProgress {

    @Id
    private String id;

    private String userId;
    private String conceptId;
    private String subjectId;
    private String subjectTitle;
    private String subjectIcon;

    // Set manually — not @CreatedDate so we control the exact timestamp
    private LocalDateTime completedAt;
}
