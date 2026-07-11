package com.example.student.dto;

import lombok.*;

import java.util.List;

/**
 * A group card shown on a category page (e.g. "Number Basics").
 * Carries display metadata plus its live topic count.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AptitudeGroupDTO {
    private String slug;
    private String category;
    private String displayName;
    private String description;
    private String icon;
    private int order;
    private long topicCount;
    // Names of the group's active topics — lets the category page search a group
    // by the topics it contains (e.g. "relative speed" surfaces Time-Speed-Distance).
    private List<String> topicNames;
}
