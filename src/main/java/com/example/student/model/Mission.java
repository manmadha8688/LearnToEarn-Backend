package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "missions")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Mission {

    @Id
    private String id;

    private String title;
    private String missionBrief;
    private String rank; // D, C, B, A, S

    private List<String> techStack;
    private int estimatedHours;

    // One mission links to multiple subjects
    private List<String> subjectIds;
    private List<String> subjectTitles;

    private List<String> objectives;
    private List<String> bonusObjectives;
    private List<String> hints;
    private List<String> approachSteps;

    @Builder.Default
    private boolean published = true;

    @Builder.Default
    private int orderIndex = 0;

    @CreatedDate
    private LocalDateTime createdAt;
}
