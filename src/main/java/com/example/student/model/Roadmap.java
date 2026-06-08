package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "roadmaps")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Roadmap {

    @Id
    private String id;

    private String title;
    private String description;
    private String roleTarget;

    @Builder.Default
    private String icon = "🗺️";

    @Builder.Default
    private String color = "#7C3AED";

    @Builder.Default
    private int estimatedWeeks = 12;

    @Builder.Default
    private boolean isPublished = true;

    // Multiple target roles
    private List<String> roleTargets;

    // Rich info fields
    private String overview;
    private String whyLearn;
    private String forWho;
    private List<String> prerequisites;
    private List<String> toolsRequired;
    private List<String> outcomes;

    @CreatedDate
    private LocalDateTime createdAt;
}
