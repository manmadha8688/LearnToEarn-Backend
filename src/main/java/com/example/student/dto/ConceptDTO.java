package com.example.student.dto;

import com.example.student.model.Concept;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @NoArgsConstructor
public class ConceptDTO {
    private String id;
    private String title;

    // legacy
    private String whatItIs;
    private String whyItMatters;
    private String codeExample;

    // rich content
    private String introduction;
    private String explanationSimple;
    private String explanationTechnical;
    private String syntax;
    private List<Concept.ConceptExample> examples;
    private List<String> keyPoints;
    private String tip;
    private List<String> commonMistakes;

    private String rank;
    private int orderIndex;
    private int estimatedMinutes;
    private boolean completed;
}
