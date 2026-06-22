package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "concepts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Concept {

    @Id
    private String id;

    @Indexed
    private String subjectId;

    private String subjectTitle;
    private String subjectIcon;

    private String title;

    // ── legacy fields (kept for backward compat) ──
    private String content;
    private String whatItIs;
    private String whyItMatters;
    private String codeExample;

    // ── rich content fields ──
    private String introduction;
    private String explanationSimple;
    private String explanationTechnical;
    private String syntax;
    private List<ConceptExample> examples;
    private List<String> keyPoints;
    private String tip;
    private List<String> commonMistakes;

    // ── video ──
    private String videoUrl;   // YouTube URL e.g. https://www.youtube.com/watch?v=xxx
    private String videoTitle; // optional label shown under embed

    @Builder.Default
    private String rank = "E";

    @Builder.Default
    private int estimatedMinutes = 15;

    @Builder.Default
    private int orderIndex = 0;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Getter @Setter @NoArgsConstructor @AllArgsConstructor
    public static class ConceptExample {
        private String title;
        private String description;
        private String code;
        private String output;
        private String demoHtml;
    }
}
