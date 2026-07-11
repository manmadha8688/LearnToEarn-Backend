package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A group of related aptitude topics inside a category
 * (e.g. "Number Basics" inside Quantitative).
 *
 * The nesting is: category (4 fixed, static in frontend) → group → topic.
 * Groups are seeded from resources/seed/aptitude-taxonomy.json alongside topics.
 * They carry only light display metadata — the actual lessons live on the topics.
 */
@Document(collection = "aptitude_groups")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AptitudeGroup {

    @Id
    private String id;

    // quantitative | logical | verbal | data-interpretation
    private String category;

    // URL-safe slug, unique per group — used as the :group route param
    @Indexed(unique = true)
    private String slug;

    private String displayName;
    private String description;   // one line — what this group covers
    private String icon;          // emoji

    private int order;            // display order inside its category
    private boolean isActive;
}
