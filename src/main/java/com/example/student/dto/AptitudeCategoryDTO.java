package com.example.student.dto;

import lombok.*;

/**
 * Lightweight summary for an aptitude category card.
 * Display metadata (name, icon, description) lives statically in the frontend —
 * the backend only owns the topic count, derived from the database.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class AptitudeCategoryDTO {
    private String category;   // quantitative | logical | verbal | data-interpretation
    private long topicCount;
}
