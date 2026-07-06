package com.example.student.model;

import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * A user's "save for later" entry. One row per (user, type, ref) — the unique index
 * (created in DataIntegrityMigration) prevents duplicates. No progress data is stored.
 */
@Document(collection = "bookmarks")
@CompoundIndex(def = "{'userId': 1, 'type': 1, 'refId': 1}", unique = true)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Bookmark {

    @Id
    private String id;

    private String userId;

    // SUBJECT | ROADMAP | MISSION | PROBLEM
    private String type;

    private String refId;

    // Snapshot of display fields so the list renders without extra lookups.
    private String title;
    private String description;
    private String icon;

    @CreatedDate
    private LocalDateTime createdAt;
}
