package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "subjects")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Subject {

    @Id
    private String id;

    private String title;
    private String description;

    @Builder.Default
    private String icon = "📚";

    @Builder.Default
    private String color = "#4F46E5";

    @Builder.Default
    private int totalConcepts = 0;

    @Builder.Default
    private String rank = "E";

    // ── Rich subject info ─────────────────────────────
    private String overview;
    private String whyLearn;
    private String forWho;
    private List<String> prerequisites;
    private List<String> outcomes;
    private List<String> whatYouWillBuild;
    private String difficulty;
    private int estimatedHours;
    private String careerUse;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
